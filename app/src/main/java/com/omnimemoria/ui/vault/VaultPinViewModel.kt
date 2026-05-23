package com.omnimemoria.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.data.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest

data class PinUiState(
    val digits: List<Int?> = List(4) { null },
    val step: PinStep = PinStep.ENTER,
    val message: String? = null,
    val errorTick: Int = 0,
    val success: Boolean = false
)

enum class PinStep { ENTER, CONFIRM, UNLOCKING, LOCKED_OUT }

@HiltViewModel
class VaultPinViewModel @Inject constructor(
    private val vaultRepository: VaultRepository
) : ViewModel() {
    val vaultSetup: StateFlow<Boolean> = vaultRepository.isVaultSetupFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _pinState = MutableStateFlow(PinUiState())
    val pinState: StateFlow<PinUiState> = _pinState.asStateFlow()

    private val _attemptsLeft = MutableStateFlow(5)
    val attemptsLeft: StateFlow<Int> = _attemptsLeft.asStateFlow()

    private val _lockoutRemainingSeconds = MutableStateFlow(0)
    val lockoutRemainingSeconds: StateFlow<Int> = _lockoutRemainingSeconds.asStateFlow()

    private var entered = ""
    private var confirm = ""
    private var firstSetupPin: String? = null

    fun enterDigit(digit: Int) {
        if (_pinState.value.step == PinStep.LOCKED_OUT) return
        if (entered.length >= 4) return
        entered += digit.toString()
        _pinState.value = _pinState.value.copy(digits = entered.toDigitList(), message = null)
        if (entered.length == 4) submitPin()
    }

    fun deleteDigit() {
        if (entered.isEmpty()) return
        entered = entered.dropLast(1)
        _pinState.value = _pinState.value.copy(digits = entered.toDigitList(), message = null)
    }

    fun submitPin() {
        if (entered.length != 4) return
        if (!vaultSetup.value) handleSetup()
        else handleUnlock()
    }

    private fun handleSetup() {
        if (_pinState.value.step == PinStep.CONFIRM) {
            confirm = entered
            if (confirm == firstSetupPin) {
                viewModelScope.launch {
                    vaultRepository.savePinHash(sha256(confirm))
                    _pinState.value = PinUiState(step = PinStep.UNLOCKING, success = true, message = "Vault unlocked")
                }
            } else {
                firstSetupPin = null
                entered = ""
                confirm = ""
                _pinState.value = _pinState.value.copy(
                    digits = List(4) { null },
                    step = PinStep.ENTER,
                    message = "PINs don't match",
                    errorTick = _pinState.value.errorTick + 1
                )
            }
            return
        }
        firstSetupPin = entered
        entered = ""
        _pinState.value = PinUiState(
            digits = List(4) { null },
            step = PinStep.CONFIRM,
            message = "Re-enter your PIN"
        )
    }

    private fun handleUnlock() {
        val typed = entered
        viewModelScope.launch {
            val valid = vaultRepository.verifyPinHash(sha256(typed))
            if (valid) {
                _attemptsLeft.value = 5
                _pinState.value = PinUiState(step = PinStep.UNLOCKING, success = true, message = null)
            } else {
                val left = (_attemptsLeft.value - 1).coerceAtLeast(0)
                _attemptsLeft.value = left
                if (left == 0) startLockout()
                else {
                    entered = ""
                    _pinState.value = _pinState.value.copy(
                        digits = List(4) { null },
                        message = "Incorrect PIN",
                        errorTick = _pinState.value.errorTick + 1
                    )
                }
            }
        }
    }

    private fun startLockout() {
        viewModelScope.launch {
            entered = ""
            _pinState.value = PinUiState(step = PinStep.LOCKED_OUT, message = "Too many attempts. Try again in 30 seconds")
            _lockoutRemainingSeconds.value = 30
            while (_lockoutRemainingSeconds.value > 0) {
                delay(1_000)
                _lockoutRemainingSeconds.value -= 1
            }
            _attemptsLeft.value = 5
            _pinState.value = PinUiState(step = PinStep.ENTER)
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun String.toDigitList(): List<Int?> = List(4) { index ->
        getOrNull(index)?.digitToIntOrNull()
    }
    companion object {
}

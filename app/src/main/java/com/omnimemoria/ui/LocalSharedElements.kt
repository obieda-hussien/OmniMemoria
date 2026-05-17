package com.omnimemoria.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Provides the [SharedTransitionScope] created once in [AppNavGraph]
 * so any descendant composable can participate in shared-element transitions
 * without threading the scope through every call site.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * Provides the [AnimatedVisibilityScope] for the CURRENT navigation destination.
 * Updated by [AppNavGraph] on every composable entry via [CompositionLocalProvider].
 * Using [compositionLocalOf] (not static) so recomposition happens when
 * navigating between destinations.
 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

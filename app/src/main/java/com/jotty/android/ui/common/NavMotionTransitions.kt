package com.jotty.android.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry

fun navEnterTransition(reducedMotion: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    {
        if (reducedMotion) EnterTransition.None else fadeIn()
    }

fun navExitTransition(reducedMotion: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    {
        if (reducedMotion) ExitTransition.None else fadeOut()
    }

fun navPopEnterTransition(reducedMotion: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    navEnterTransition(reducedMotion)

fun navPopExitTransition(reducedMotion: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    navExitTransition(reducedMotion)
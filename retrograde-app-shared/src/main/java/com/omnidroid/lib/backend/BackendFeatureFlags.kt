package com.omnidroid.lib.backend

object BackendFeatureFlags {
    /**
     * When true, Switch launches route to BaseGameActivity/GameActivity via the unified
     * backend session architecture. When false, routes to legacy SwitchGameActivity.
     */
    var USE_UNIFIED_SWITCH_BACKEND = true
}


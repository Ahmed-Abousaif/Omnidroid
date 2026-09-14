package com.swordfish.lemuroid.app.mobile.shared.controller

data class ControllerHint(
    val keys: List<String>,
    val label: String,
)

data class ControllerHints(
    val left: List<ControllerHint> = emptyList(),
    val right: List<ControllerHint> = emptyList(),
) {
    companion object {
        fun library(
            switchConsoles: String,
            zoom: String,
            select: String,
            back: String,
            options: String,
            search: String,
        ) =
            ControllerHints(
                left =
                    listOf(
                        ControllerHint(listOf("L1", "R1"), switchConsoles),
                        ControllerHint(listOf("L2", "R2"), zoom),
                    ),
                right =
                    listOf(
                        ControllerHint(listOf("A"), select),
                        ControllerHint(listOf("B"), back),
                        ControllerHint(listOf("X"), options),
                        ControllerHint(listOf("Y"), search),
                    ),
            )

        fun carousel(switchConsoles: String, select: String, back: String) =
            ControllerHints(
                left =
                    listOf(
                        ControllerHint(listOf("L1", "R1"), switchConsoles),
                    ),
                right =
                    listOf(
                        ControllerHint(listOf("A"), select),
                        ControllerHint(listOf("B"), back),
                    ),
            )

        fun standard(select: String, back: String, options: String? = null) =
            ControllerHints(
                right =
                    buildList {
                        add(ControllerHint(listOf("A"), select))
                        add(ControllerHint(listOf("B"), back))
                        if (options != null) {
                            add(ControllerHint(listOf("X"), options))
                        }
                    },
            )
    }
}

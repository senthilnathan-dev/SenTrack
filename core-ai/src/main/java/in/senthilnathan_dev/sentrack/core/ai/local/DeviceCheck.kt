package `in`.senthilnathan_dev.sentrack.core.ai.local

/** What the phone reports about itself. Read once by the app and passed in, so the rules below are plain testable maths. */
data class DeviceProfile(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val freeStorageMb: Long,
    val abis: List<String>,
    val sdkInt: Int,
    val lowRamDevice: Boolean,
    /** Android's thermal status 0 (none) to 6 (shutdown), or null when the phone does not say. */
    val thermalStatus: Int?,
)

enum class Level { OK, WARN, FAIL }

data class Check(val label: String, val level: Level, val detail: String)

/** The quick checks run before a model is even loaded. */
object DeviceCheck {
    /**
     * @param modelSizeMb size of the model file. Storage needs room for the file plus a working copy while importing.
     */
    fun evaluate(p: DeviceProfile, modelSizeMb: Int): List<Check> = buildList {
        add(
            if ("arm64-v8a" in p.abis) Check("Processor", Level.OK, "64-bit ARM, supported.")
            else Check("Processor", Level.FAIL, "This phone has no 64-bit ARM processor, which the on-device runtime needs."),
        )

        val ramNeeded = modelSizeMb * 3L / 2 + 700   // the model, its working memory, plus room for Android and SenTrack
        add(
            when {
                p.totalRamMb < 4_000 -> Check("Memory", Level.FAIL, "${p.totalRamMb} MB of RAM is too little for a language model.")
                p.availableRamMb < ramNeeded -> Check(
                    "Memory", Level.WARN,
                    "About ${p.availableRamMb} MB is free now and roughly $ramNeeded MB is wanted. Close other apps first; it may still run.",
                )
                else -> Check("Memory", Level.OK, "${p.availableRamMb} MB free of ${p.totalRamMb} MB.")
            },
        )

        val storageNeeded = modelSizeMb * 2L + 200
        add(
            if (p.freeStorageMb >= storageNeeded) Check("Storage", Level.OK, "${p.freeStorageMb} MB free.")
            else Check("Storage", Level.FAIL, "Needs about $storageNeeded MB free to import the model; ${p.freeStorageMb} MB is free."),
        )

        add(
            if (p.lowRamDevice) Check("Phone class", Level.WARN, "Android marks this as a low-memory phone, so it may kill the model under load.")
            else Check("Phone class", Level.OK, "Regular memory class."),
        )

        val t = p.thermalStatus
        add(
            when {
                t == null -> Check("Heat", Level.OK, "No heat reading available.")
                t >= 3 -> Check("Heat", Level.FAIL, "The phone is running hot. Let it cool down, then test again.")
                t == 2 -> Check("Heat", Level.WARN, "The phone is warm; results may be slower than usual.")
                else -> Check("Heat", Level.OK, "Cool.")
            },
        )
    }

    /** The test may proceed unless something failed outright. Warnings are shown but do not block. */
    fun canProceed(checks: List<Check>): Boolean = checks.none { it.level == Level.FAIL }
}

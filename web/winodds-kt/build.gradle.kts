// Compiles the app's own win-turn code (app/src/main/java/com/mtgtrader/data) to JavaScript, so the website's numbers
// are the app's. The app's files are copied (prepareSources) with the few JVM-only parts swapped out; the result goes
// to web/src/lib/winodds-js (copyToWeb). Run: ./gradlew -p web/winodds-kt copyToWeb (JDK 18).
plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

val localBuildRoot = System.getenv("LOCALAPPDATA")?.let { File(it, "mtgtrader-build") }
if (localBuildRoot != null) layout.buildDirectory.set(File(localBuildRoot, "winodds-kt"))

val appData = rootDir.resolve("../../app/src/main/java/com/mtgtrader/data")
val generated = layout.buildDirectory.dir("generated/app")

kotlin {
    js {
        nodejs()
        useEsModules()
        binaries.library()
    }
    sourceSets {
        jsMain {
            kotlin.srcDir(generated)
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
    }
}

/** Replaces [old] with [new] exactly [times] times, or fails (so a change in the app's file is noticed). */
fun String.swap(old: String, new: String, times: Int = 1): String {
    val found = split(old).size - 1
    require(found == times) { "Expected $times of \"${old.take(60)}\", found $found" }
    return replace(old, new)
}

val prepareSources by tasks.registering {
    inputs.dir(appData)
    outputs.dir(generated)
    doLast {
        val out = generated.get().asFile.resolve("com/mtgtrader/data").apply { deleteRecursively(); mkdirs() }
        fun write(name: String, text: String) = out.resolve(name).writeText(text)
        // Git on Windows may check the files out with CRLF line ends.
        fun read(name: String) = appData.resolve(name).readText().replace("\r\n", "\n")
        // Without DeckStats.tokens, which needs the app's CardInfo.
        val stats = read("DeckStats.kt")
        val tokensStart = "    /**\n     * The deck's tokens, one per token"
        val tokensEnd = "    /** The token printings decks show"
        require(tokensStart in stats && tokensEnd in stats) { "DeckStats.tokens moved" }
        write("DeckStats.kt", stats.substringBefore(tokensStart) + tokensEnd + stats.substringAfter(tokensEnd))
        write("ManaOdds.kt", read("ManaOdds.kt"))
        write("SaltCard.kt", read("SaltCard.kt"))
        // Only AppPowerLevel: the rest of AppPower.kt keeps the app's screen state.
        val power = read("AppPower.kt")
        write(
            "AppPower.kt",
            power.substringBefore("\n/**\n * The app's power levels worked out so far")
                .swap("import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.setValue\n", ""),
        )
        val win = read("WinOdds.kt")
        write(
            "WinOdds.kt",
            win.substringBefore("\n/**\n * The win turns, kept in a file on the phone")
                .swap("import kotlinx.coroutines.Dispatchers\n", "")
                .swap("import kotlinx.coroutines.sync.Mutex\n", "")
                .swap("import kotlinx.coroutines.sync.withLock\n", "")
                .swap("import kotlinx.coroutines.withContext\n", "")
                .swap("import java.io.File\n", "")
                .swap("import java.security.MessageDigest\n", "")
                // Its only pattern has no dot, so DOT_MATCHES_ALL (not in JavaScript's Kotlin) changes nothing.
                .swap("setOf(I, RegexOption.DOT_MATCHES_ALL)", "I")
                // Two members that JavaScript would give the same name.
                .swap("        fun chosenType(): String? = graveyard", "        @JsName(\"chosenTypeInGraveyard\") fun chosenType(): String? = graveyard")
                // Where a group starts (not known in JavaScript's Kotlin); each is the match's last group, "(.*)" to its end.
                .swap("dies.groups[1]!!.range.first", "dies.range.last + 1 - dies.groupValues[1].length")
                .swap("m.groups[group]!!.range.first", "m.range.last + 1 - m.groupValues[group].length")
                .swap("m.groups[3]!!.range.first", "m.range.last + 1 - m.groupValues[3].length")
                // The batches one after another, with the same seeds (no threads in JavaScript).
                .swap("            Thread {\n                try {\n                    results[b] = playBatch(", "            run {\n                try {\n                    results[b] = playBatch(")
                .swap("            }.also { it.start() }\n        }\n        threads.forEach { it.join() }\n", "            }\n        }\n")
                // Each game reports itself, for the website's progress bar (see Website.kt's WebProgress).
                .swap("        val r = Batch(turns)\n        repeat(count) {\n", "        val r = Batch(turns)\n        repeat(count) {\n            WebProgress.game()\n"),
        )
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach { dependsOn(prepareSources) }

val copyToWeb by tasks.registering(Sync::class) {
    dependsOn("compileProductionLibraryKotlinJs")
    from(layout.buildDirectory.dir("compileSync/js/main/productionLibrary/kotlin"))
    include("*.mjs")
    into(rootDir.resolve("../src/lib/winodds-js"))
    // The app's WinOddsCalc.VERSION, so the website works a deck's win turn out again when the app's model changes.
    doLast {
        val version = Regex("const val VERSION = (\\d+)").find(appData.resolve("WinOdds.kt").readText())!!.groupValues[1]
        // Its TypeScript type (Kotlin's own generator needs yarn and an npm download).
        destinationDir.resolve("winodds-kt.d.mts").writeText("export declare function winOddsJson(cardsJson: string, identity: string, saltCard: string | null, games?: number | null, onProgress?: ((started: number) => void) | null): string;\n")
        destinationDir.resolve("version.ts").writeText("// Written by web/winodds-kt (copyToWeb): the app's WinOddsCalc.VERSION.\nexport const WIN_ODDS_VERSION = $version;\n")
    }
}

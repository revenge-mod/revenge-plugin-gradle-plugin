package io.github.revenge.plugin.gradle

import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownProjectException
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import kotlin.io.path.Path
import kotlin.io.path.pathString

@Suppress("unused")
abstract class JsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.configureDependencies()
        project.configureArtifactSharing()
        project.configureJarTask(extension)
    }


    /**
     * Setup sharing the extension dex file with the consuming patches project.
     */
    private fun Project.configureArtifactSharing() {
        val androidExtension = extensions.getByType<BaseAppModuleExtension>()
        val syncExtensionTask = tasks.register<Sync>("syncExtension") {
            val dexTaskName = if (androidExtension.buildTypes.getByName("release").isMinifyEnabled) {
                "minifyReleaseWithR8"
            } else {
                "mergeDexRelease"
            }

            val dexTask = tasks.getByName(dexTaskName)

            dependsOn(dexTask)

            val extensionName = if (extension.name != null) {
                Path(extension.name!!)
            } else {
                projectDir.resolveSibling(project.name + ".rve").relativeTo(rootDir).toPath()
            }

            from(dexTask.outputs.files.asFileTree.matching { include("**/*.dex") })
            into(layout.buildDirectory.dir("revanced/${extensionName.parent.pathString}"))

            rename { extensionName.fileName.toString() }
        }

        configurations.create("extensionConfiguration").apply {
            isCanBeResolved = false
            isCanBeConsumed = true

            outgoing.artifact(layout.buildDirectory.dir("revanced")) {
                it.builtBy(syncExtensionTask)
            }
        }
    }


    /**
     * Adds the dependencies ReVanced Patcher and SMALI to the project.
     * The versions are fetched from the version catalog by the respective project.
     */
    private fun Project.configureDependencies() {
        afterEvaluate {
            val catalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

            operator fun String.invoke(versionAlias: String) = dependencies.add(
                "implementation",
                "$this:" + catalog.findVersion(versionAlias).orElseThrow {
                    IllegalArgumentException("Version with alias $versionAlias not found in version catalog")
                },
            )

            "app.revanced:revanced-patcher"("revanced-patcher")
            "com.android.tools.smali:smali"("smali")
        }
    }

    /**
     * Configure the manifest file with the "about" information from the extension.
     */
    private fun Project.configureJarTask(pluginExtension: PluginExtension) {
        tasks.withType(Jar::class.java).configureEach {
            it.archiveExtension.set("rp")
            it.manifest.apply {
                attributes["Name"] = pluginExtension.about.name
                attributes["Description"] = pluginExtension.about.description
                attributes["Version"] = project.version.toString()
                attributes["Timestamp"] = System.currentTimeMillis().toString()
                attributes["Source"] = pluginExtension.about.source
                attributes["Author"] = pluginExtension.about.author
                attributes["Contact"] = pluginExtension.about.contact
                attributes["Website"] = pluginExtension.about.website
                attributes["License"] = pluginExtension.about.license
            }
        }
    }
}
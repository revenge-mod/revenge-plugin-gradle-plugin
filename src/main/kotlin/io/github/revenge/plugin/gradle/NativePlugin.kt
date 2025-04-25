package io.github.revenge.plugin.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import kotlinx.validation.BinaryCompatibilityValidatorPlugin
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownProjectException
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.jvm.java

@Suppress("unused")
abstract class NativePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("js", PluginExtension::class.java)

        project.configureAndroid()
        project.configureBinaryCompatibilityValidator()
        project.configureSigning()
        project.configureConsumeJs()
    }


    /**
     * Configures the project to consume the extension artifacts and add them to the resources of the patches project.
     */
    private fun Project.configureConsumeJs() {
        val extensionsProject = try {
            project(pluginExtension.extensionsProjectPath ?: return)
        } catch (e: UnknownProjectException) {
            return
        }

        val extensionProjects = extensionsProject.subprojects.filter { extensionProject ->
            extensionProject.plugins.hasPlugin(NativePlugin::class.java)
        }

        val extensionsDependencyScopeConfiguration =
            configurations.dependencyScope("extensionsDependencyScope").get()
        val extensionsConfiguration = configurations.resolvable("extensionConfiguration").apply {
            configure { it.extendsFrom(extensionsDependencyScopeConfiguration) }
        }

        project.dependencies.apply {
            extensionProjects.forEach { extensionProject ->
                add(
                    extensionsDependencyScopeConfiguration.name,
                    project(
                        mapOf(
                            "path" to extensionProject.path,
                            "configuration" to "extensionConfiguration",
                        ),
                    ),
                )
            }
        }

        extensions.configure<SourceSetContainer>("sourceSets") { sources ->
            sources.named("main") { main ->
                main.resources.srcDir(extensionsConfiguration)
            }
        }
    }

    /**
     * Configures the signing plugin to sign the patches publication.
     */
    private fun Project.configureSigning() {
        pluginManager.apply("signing")

        extensions.configure<SigningExtension>("signing") {
            it.useGpgCmd()
            extensions.getByType(PublishingExtension::class.java).publications
                .named("ReVancedPatches").configure(it::sign)
        }
    }

    /**
     * Applies the binary compatibility validator plugin to the project, because patches have a public API.
     */
    private fun Project.configureBinaryCompatibilityValidator() {
        pluginManager.apply(BinaryCompatibilityValidatorPlugin::class.java)
    }

    /**
     * Set up the Android plugin for the extension project.
     */
    private fun Project.configureAndroid() {
        pluginManager.apply {
            apply(AppPlugin::class.java)
            apply(KotlinAndroidPluginWrapper::class.java)
        }

        extensions.configure(BaseAppModuleExtension::class.java) {
            it.apply {
                compileSdk = 34
                namespace = settingsExtensionProvider.parameters.defaultNamespace

                defaultConfig {
                    minSdk = 23
                    multiDexEnabled = false
                }

                buildTypes {
                    release {
                        isMinifyEnabled = settingsExtensionProvider.parameters.proguardFiles.isNotEmpty()

                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            *settingsExtensionProvider.parameters.proguardFiles.toTypedArray(),
                        )
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                this as ExtensionAware
                this.extensions.configure<KotlinJvmOptions>("kotlinOptions") { options ->
                    options.jvmTarget = JavaVersion.VERSION_17.toString()
                }
            }
        }
    }
}

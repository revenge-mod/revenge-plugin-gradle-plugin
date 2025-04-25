package io.github.revenge.plugin.gradle

open class PluginExtension {
    /**
     * About information for the project.
     */
    val about = About()

    fun about(block: About.() -> Unit) {
        about.block()
    }

    /**
     * About information for the project.
     *
     * Used by the plugin project to create the manifest file.
     */
    class About {
        var name: String? = null
        var description: String? = null
        var source: String? = null
        var author: String? = null
        var contact: String? = null
        var website: String? = null
        var license: String? = null
    }
}

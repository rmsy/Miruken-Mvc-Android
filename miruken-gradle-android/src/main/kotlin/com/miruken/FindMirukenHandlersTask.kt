package com.miruken

import org.gradle.api.DefaultTask
import java.io.File
import io.github.classgraph.ClassGraph
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

open class FindMirukenHandlersTask : DefaultTask() {

    private val sep           = java.io.File.separator
    private val projectName   = project.name.toLowerCase().replace("-", "_")
    private val mirukenDir    = "${project.buildDir}${sep}generated${sep}source${sep}miruken$sep"
            val resDir        = "${mirukenDir}res$sep"
    private val rawDir        = "${resDir}raw$sep"

    private var targetVariant = "Debug"
    private var studioBuild   = true

    private val resourceName
        get() = "${projectName}_handlers"

    private val scanResult
        get() = "$rawDir$resourceName.txt"

    private val compileTask : KotlinCompile
        get() = try {
                    project.logger.debug("using compileTask: compile${targetVariant}Kotlin")
                    project.tasks.getByName("compile${targetVariant}Kotlin") as KotlinCompile
                } catch (exception: Exception){
                    throw GradleException("Kotlin build plugin is not present")
                }

    init {
        val android   = project.android
        val buildType = android.buildTypes
                .create(CLASS_GRAPH_VARIANT)

        android.buildTypes.all { b ->
            if (b.name.toLowerCase() == "debug"){
                buildType.initWith(b)
            }
        }

        project.variants.all { variant ->
            when (variant.name) {
                CLASS_GRAPH_VARIANT -> {
                    targetVariant = CLASS_GRAPH_VARIANT.capitalize()
                    studioBuild   = false
                }
                else -> {
                    variant.registerGeneratedResFolders(project
                            .files(listOf(resDir))
                            .builtBy(this))
                }
            }
        }

        project.afterEvaluate{
            if(!studioBuild){
                dependsOn(compileTask)
            }
        }
    }

    @TaskAction
    fun runClassGraph() {
        project.logger.debug("**** studioBuild: $studioBuild targetVariant: $targetVariant")
        project.variants.forEach {
            project.logger.debug("**** available variant: ${it.name}")
        }

        //create output directories
        File(rawDir).mkdirs()

        val usePolicy = "com.miruken.callback.policy.UsePolicy"

        //Build classPath
        val classPath = mutableListOf<String>()
        classPath.add(compileTask.destinationDir.path)
        project.configurations.findByName("debugRuntimeClasspath")?.resolve()?.forEach {
            classPath.add(it.path)
        }

        ClassGraph()
                .overrideClasspath(classPath)
                .enableAllInfo()
                .enableExternalClasses()
                .scan().use{ result ->
                    val annotations = result
                            .getClassesWithAnnotation(usePolicy)
                            .annotations
                            .names

                    val classes = result
                            .getClassesWithMethodAnnotation(usePolicy)
                            .standardClasses

                    File(scanResult).printWriter().use { p ->
                        classes.forEach { c ->
                            p.print("${c.name};")
                            c.methodAndConstructorInfo
                                    .filter{ m -> m.annotationInfo.any { a -> a.name in annotations }}
                                    .forEach { m ->
                                        p.print("${m.name} ${m.typeDescriptor};")
                                    }
                            p.print(System.lineSeparator())
                        }
                    }
                    project.logger.lifecycle("    Wrote ${classes.size} handler classes to")
                    project.logger.lifecycle("        $scanResult")
                    project.logger.lifecycle("    Refer to resource as ")
                    project.logger.lifecycle("        $resourceName")
                }
    }

    companion object {
        const val CLASS_GRAPH_VARIANT = "mirukenClassGraph"
    }
}
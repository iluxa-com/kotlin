/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_TO_JS_COMPILER_ARGUMENTS_SECTION
import org.jetbrains.kotlin.idea.compiler.configuration.BaseKotlinCompilerSettings.Companion.KOTLIN_COMPILER_SETTINGS_PATH

@State(name = KOTLIN_TO_JS_COMPILER_ARGUMENTS_SECTION,
       storages = arrayOf(Storage(file = StoragePathMacros.PROJECT_FILE),
                          Storage(file = KOTLIN_COMPILER_SETTINGS_PATH, scheme = StorageScheme.DIRECTORY_BASED)))
class Kotlin2JsCompilerArgumentsHolder : BaseKotlinCompilerSettings<K2JSCompilerArguments>() {
    override fun createSettings() = createDefaultArguments()

    override fun validateNewSettings(settings: K2JSCompilerArguments) {
        validateInheritedFieldsUnchanged(settings)
    }

    companion object {
        fun getInstance(project: Project) = ServiceManager.getService(project, Kotlin2JsCompilerArgumentsHolder::class.java)!!

        fun createDefaultArguments(): K2JSCompilerArguments = K2JSCompilerArguments()
    }
}

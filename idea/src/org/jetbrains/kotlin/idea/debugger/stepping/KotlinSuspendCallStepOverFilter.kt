/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.debugger.stepping

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.RequestHint
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.ui.breakpoints.BreakpointManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.util.Range
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.breakpoints.LineBreakpointState
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import com.sun.jdi.Location
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.kotlin.idea.debugger.breakpoints.KotlinLineBreakpointType
import org.jetbrains.kotlin.idea.debugger.isOnSuspendReturnOrReenter
import org.jetbrains.kotlin.idea.refactoring.getLineEndOffset
import org.jetbrains.kotlin.idea.util.application.runReadAction
import java.lang.reflect.Field

class KotlinSuspendCallStepOverFilter(val line: Int, val file: PsiFile) : MethodFilter {
    override fun getCallingExpressionLines(): Range<Int>? = Range(line, line) // TODO: Better default for null in isOnTheSameLine()

    override fun locationMatches(process: DebugProcessImpl, location: Location?): Boolean {
        return location != null && isOnSuspendReturnOrReenter(location)
    }

    override fun onReached(context: SuspendContextImpl, hint: RequestHint?): Int {
        val location = context.frameProxy?.location() ?: return RequestHint.STOP
        val firstMethodLineLocation = location.method().allLineLocations().firstOrNull() ?: return RequestHint.STOP
        createBreakpoint(context, firstMethodLineLocation.lineNumber() - 1, file)
        return RequestHint.RESUME
    }
}

fun createBreakpoint(context: SuspendContextImpl, line: Int, file: PsiFile): Boolean {
    val debugProcess = context.debugProcess

    val project = debugProcess.project
    val xBreakpointManager = XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl

    val virtualFile = file.virtualFile
    val offset = file.getLineEndOffset(line) ?: return false

    val kotlinLineBreakpointType = XDebuggerUtil.getInstance().findBreakpointType(KotlinLineBreakpointType::class.java)
    val breakpointProperties = kotlinLineBreakpointType.createBreakpointProperties(virtualFile, line)
    var xBreakpoint: XLineBreakpoint<JavaLineBreakpointProperties>? = null

    DebuggerInvocationUtil.invokeAndWait(project, {
        runWriteAction {
            xBreakpoint = addLineBreakpoint(xBreakpointManager, kotlinLineBreakpointType, virtualFile.url, line, breakpointProperties, true)
        }
    }, ModalityState.NON_MODAL)


    val breakpoint = BreakpointManager.getJavaBreakpoint(xBreakpoint)
    breakpoint

    if (breakpoint != null) {
        // TODO: add filter for check hit was made not from other coroutine
        try {
            tada.set(true)
        }
        finally {
            tada.set(false)
        }
        return true
    }

    return false
}

val tada = object : ThreadLocal<Boolean>() {
    override fun initialValue(): Boolean = false
}

fun <T : XBreakpointProperties<*>> addLineBreakpoint(
        breakpointManager: XBreakpointManagerImpl,
        type: XLineBreakpointType<T>,
        fileUrl: String,
        line: Int,
        properties: T?,
        temporary: Boolean): XLineBreakpoint<T> {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val state = LineBreakpointState<T>(true, type.id, fileUrl, line, temporary, 0 /* TODO: myTime */, type.defaultSuspendPolicy)

    state.group = breakpointManager.defaultGroup
    val breakpoint = object : XLineBreakpointImpl<T>(type, breakpointManager, properties, state) {
        private var sourcePositionField = XLineBreakpointImpl::class.java.getDeclaredField("mySourcePosition")

        override fun getSourcePosition(): XSourcePosition? {
            var sourcePosition: XSourcePosition? = this.getFromField<XLineBreakpointImpl<*>, XSourcePosition?>(sourcePositionField)

            if (sourcePosition == null) {
                runReadAction {
                    val vFile = VirtualFileManager.getInstance().findFileByUrl(getFileUrl())
                    val document = document
                    sourcePosition = XDebuggerUtil.getInstance().createPosition(vFile, getLine(), 1) // Mark position for PositionManager
                    setToField(sourcePositionField, sourcePosition)
                }
            }


            return sourcePosition
        }


    }

    breakpointManager.addBreakpointReflection(breakpoint, false, true)
    return breakpoint
}

private fun <R, T> R.getFromField(field: Field?): T {
    field!!.isAccessible = true

    @Suppress("UNCHECKED_CAST")
    return field.get(this) as T
}

private fun <R, T> R.setToField(field: Field?, value: T) {
    field!!.isAccessible = true

    @Suppress("UNCHECKED_CAST")
    field.set(this, value)
}

private fun XBreakpointManagerImpl.addBreakpointReflection(breakpoint: XBreakpointBase<*, *, *>, defaultBreakpoint: Boolean, initUI: Boolean) {
    val addBreakpointMethod = XBreakpointManagerImpl::class.java.getDeclaredMethod(
            "addBreakpoint",
            XBreakpointBase::class.java, Boolean::class.java, Boolean::class.java)

    addBreakpointMethod.isAccessible = true

    addBreakpointMethod.invoke(this, breakpoint, defaultBreakpoint, initUI)
}

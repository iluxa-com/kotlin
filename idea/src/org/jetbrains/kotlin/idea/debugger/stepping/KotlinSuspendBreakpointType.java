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

package org.jetbrains.kotlin.idea.debugger.stepping;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.debugger.ui.breakpoints.RunToCursorBreakpoint;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

public class KotlinSuspendBreakpointType extends JavaLineBreakpointType {
    @Override
    public boolean matchesPosition(
            @NotNull LineBreakpoint<?> breakpoint, @NotNull SourcePosition position
    ) {
        // TODO:
        JavaBreakpointProperties properties = getProperties(breakpoint);
        if (properties instanceof JavaLineBreakpointProperties) {
            if (!(breakpoint instanceof RunToCursorBreakpoint) && ((JavaLineBreakpointProperties)properties).getLambdaOrdinal() == null) return true;
            PsiElement containingMethod = getContainingMethod(breakpoint);
            if (containingMethod == null) return false;
            return DebuggerUtilsEx.inTheMethod(position, containingMethod);
        }
        return true;
    }

    @Nullable
    private static JavaBreakpointProperties getProperties(@NotNull LineBreakpoint<?> breakpoint) {
        XBreakpoint<?> xBreakpoint = breakpoint.getXBreakpoint();
        return xBreakpoint != null ? (JavaBreakpointProperties) xBreakpoint.getProperties() : null;
    }
}

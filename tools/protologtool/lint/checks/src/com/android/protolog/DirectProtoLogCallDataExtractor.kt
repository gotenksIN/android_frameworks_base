/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.protolog

import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class DirectProtoLogCallDataExtractor : LogCallDataExtractor {
    override fun extractCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ): ProtoLogCall? {
        if (!isDirectProtoLogCall(context, node, method)) {
            return null
        }

        if (node.valueArguments.size < 2) {
            return null
        }

        val formatStringArg = node.valueArguments[1]
        val formatArgs = node.valueArguments.subList(2, node.valueArguments.size)
        val formatString = formatStringArg.evaluate() as? String

        return ProtoLogCall(node, formatStringArg, formatString, formatArgs)
    }

    companion object {
        fun isDirectProtoLogCall(
            context: JavaContext,
            node: UCallExpression,
            method: PsiMethod
        ): Boolean {
            if (node.methodName !in listOf("v", "d", "i", "w", "e", "wtf")) {
                return false
            }
            return context.evaluator.isMemberInSubClassOf(
                method,
                "com.android.internal.protolog.ProtoLog",
                false,
            )
        }
    }
}
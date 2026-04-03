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
import org.jetbrains.uast.UExpression

class BubbleLogCallDataExtractor : LogCallDataExtractor {
    override fun extractCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ): ProtoLogCall? {
        if (!isLogCall(context, node, method)) return null

        val formatStringArg: UExpression
        val formatArgs = mutableListOf<UExpression>()
        val mapping = context.evaluator.computeArgumentMapping(node, method)

        val isJavaCall = context.uastFile?.sourcePsi?.language?.id == "JAVA"

        if (mapping.isNotEmpty()) {
            val formatParam =
                method.parameterList.parameters.firstOrNull {
                    it.name == "message" || it.name == "msg"
                } ?: method.parameterList.parameters.getOrNull(0)
            var foundFormatArg: UExpression? = null

            for (arg in node.valueArguments) {
                val param = mapping[arg]
                if (param == formatParam) {
                    foundFormatArg = arg
                } else if (param != null &&
                    (param.name == "args" || param.name == "parameters" || param.isVarArgs ||
                            param.type.canonicalText.contains("Array") ||
                            param.type.canonicalText == "java.lang.Object[]")
                ) {
                    formatArgs.add(arg)
                } else if (isJavaCall && param != null && param.name == "eventData") {
                    // In Java, calling Kotlin methods with default arguments and positional
                    // params can cause arguments to map incorrectly to eventData due to arity
                    // mismatch. We must capture all arguments passed as format arguments to
                    // validate them.
                    formatArgs.add(arg)
                }
            }
            if (foundFormatArg == null) return null
            formatStringArg = foundFormatArg
        } else {
            if (node.valueArguments.isEmpty()) return null
            formatStringArg = node.valueArguments[0]
            formatArgs.addAll(node.valueArguments.subList(1, node.valueArguments.size))
        }

        val formatString = formatStringArg.evaluate() as? String
        return ProtoLogCall(node, formatStringArg, formatString, extractVarargs(formatArgs))
    }

    private val supportedClasses = listOf(
        "com.android.wm.shell.shared.bubbles.logging.BubbleLog",
        "com.android.wm.shell.shared.bubbles.logging.DebugLogger",
        "com.android.wm.shell.bubbles.logging.BubbleProtoLog"
    )

    private fun isLogCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ): Boolean {
        if (node.methodName !in listOf("v", "d", "i", "w", "e", "wtf", "log")) {
            return false
        }
        return supportedClasses.any { className ->
            context.evaluator.isMemberInSubClassOf(method, className, false)
        }
    }

    private fun extractVarargs(args: List<UExpression>): List<UExpression> {
        if (args.size == 1) {
            var arg = args[0]
            while (arg is org.jetbrains.uast.UParenthesizedExpression) {
                arg = arg.expression
            }

            // Unwrap Kotlin named arguments
            if (arg.javaClass.simpleName == "KotlinUNamedExpression") {
                var foundInnerCall: UExpression? = null
                arg.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        if (foundInnerCall == null) {
                            foundInnerCall = node
                        }
                        return true
                    }
                })
                if (foundInnerCall != null) {
                    arg = foundInnerCall!!
                }
            }

            if (arg is UCallExpression) {
                if (arg.kind == org.jetbrains.uast.UastCallKind.NEW_ARRAY_WITH_INITIALIZER) {
                    return arg.valueArguments
                }
                if (
                    arg.methodName != null &&
                    (arg.methodName == "arrayOf" || arg.methodName!!.endsWith("ArrayOf"))
                ) {
                    return arg.valueArguments
                }
            }
        }
        return args
    }
}
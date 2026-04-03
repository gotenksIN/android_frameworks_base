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

import com.android.protolog.DirectProtoLogCallDataExtractor.Companion.isDirectProtoLogCall
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

class IndirectProtoLogCallDataExtractor : LogCallDataExtractor {
    override fun extractCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ): ProtoLogCall? {
        val internalCall = findInnerProtoLogCall(context, method) ?: return null

        val internalArgs = internalCall.valueArguments
        if (internalArgs.size < 2) {
            return null
        }

        val internalFormatArg = internalArgs[1]
        // If the format string is not a parameter of the wrapper method (or depends on it),
        // we don't need to validate the indirect ProtoLog call site.
        if (!checkDependsOnParameter(internalFormatArg, method)) {
            return null
        }

        val mapping = context.evaluator.computeArgumentMapping(node, method)

        val formatString =
            evaluateSubstituted(internalFormatArg, node, method, mapping) as? String

        val effectiveArgs = mutableListOf<UExpression>()

        for (i in 2 until internalArgs.size) {
            val arg = internalArgs[i]
            val paramIndex = getParameterIndex(arg, method)
            if (paramIndex != null) {
                val param: PsiParameter = method.parameterList.parameters[paramIndex]
                val mappedArgs = mapping.filterValues { it == param }.keys

                if (param.isVarArgs) {
                    for (callArg in node.valueArguments) {
                        if (mapping[callArg] == param) {
                            effectiveArgs.add(callArg)
                        }
                    }
                } else {
                    val mappedArg = mappedArgs.firstOrNull()
                    if (mappedArg != null) {
                        effectiveArgs.add(mappedArg)
                    } else {
                        effectiveArgs.add(arg)
                    }
                }
            } else {
                effectiveArgs.add(arg)
            }
        }

        return ProtoLogCall(node, internalFormatArg, formatString, effectiveArgs)
    }

    private fun checkDependsOnParameter(expression: UExpression, method: PsiMethod): Boolean {
        var depends = false
        expression.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
            override fun visitElement(node: UElement): Boolean {
                if (depends) return true
                if (node is UExpression) {
                    val paramIndex = getParameterIndex(node, method)
                    if (paramIndex != null) {
                        depends = true
                        return true
                    }
                }
                return false
            }
        })
        return depends
    }

    private fun evaluateSubstituted(
        expression: UExpression,
        call: UCallExpression,
        method: PsiMethod,
        mapping: Map<UExpression, PsiParameter>
    ): Any? {
        var expr = expression
        while (expr is org.jetbrains.uast.UParenthesizedExpression) {
            expr = expr.expression
        }

        // Minimal evaluator supporting string concat and interpolation
        if (expr is org.jetbrains.uast.UPolyadicExpression) {
            val sb = StringBuilder()
            for (operand in expr.operands) {
                val valObj = evaluateSubstituted(operand, call, method, mapping) ?: return null
                sb.append(valObj.toString())
            }
            return sb.toString()
        }

        // Check if expression is a reference to a parameter
        val paramIndex = getParameterIndex(expr, method)
        if (paramIndex != null) {
            val param = method.parameterList.parameters[paramIndex]
            val mappedArg = mapping.entries.firstOrNull { it.value == param }?.key
            return mappedArg?.evaluate()
        }

        return expr.evaluate()
    }

    private fun getParameterIndex(expression: UExpression, method: PsiMethod): Int? {
        var expr = expression
        while (expr is org.jetbrains.uast.UParenthesizedExpression) {
            expr = expr.expression
        }

        var resolved = (expr as? org.jetbrains.uast.UReferenceExpression)?.resolve()

        if (resolved == null && expr.sourcePsi?.text?.startsWith("*") == true) {
            // Handle spread operator which might be wrapped in USpreadExpression
            // We scan children for the reference
            var foundRef: com.intellij.psi.PsiElement? = null
            expr.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
                override fun visitElement(node: UElement): Boolean {
                    if (foundRef != null) return true
                    if (node is org.jetbrains.uast.UReferenceExpression) {
                        foundRef = node.resolve()
                        return true
                    }
                    return false
                }
            })
            resolved = foundRef
        }

        if (resolved is PsiParameter) {
            val index = method.parameterList.parameters.indexOf(resolved)
            if (index == -1) {
                return null
            }
            return index
        }
        return null
    }

    private fun findInnerProtoLogCall(
        context: JavaContext,
        method: PsiMethod
    ): UCallExpression? {
        val uMethod = method.toUElementOfType<UMethod>() ?: return null
        var foundCall: UCallExpression? = null

        uMethod.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (foundCall != null) return true

                val resolved = node.resolve()
                if (resolved != null && isDirectProtoLogCall(context, node, resolved)) {
                    foundCall = node
                    return true
                }
                return false
            }
        })
        return foundCall
    }
}
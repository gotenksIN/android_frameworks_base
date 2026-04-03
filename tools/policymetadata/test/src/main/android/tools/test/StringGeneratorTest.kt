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

package android.processor.devicepolicy.test

import android.processor.devicepolicy.protos.PolicyMetadata
import android.tools.policymetadata.Generator
import org.junit.Test

class StringGeneratorTest {

    private fun stringTestPolicy(name: String): PolicyMetadata.Builder {
        return PolicyMetadata.newBuilder().apply {
            identifier = simpleNameToFieldName(name)
            typeSpecificMetadataBuilder.stringMetadataBuilder.resolutionMechanismBuilder //
                .custom = true
        }
    }

    @Test
    fun test_outputMatches() {
        val javaFile =
            Generator.generate(
                stringTestPolicy("test.package.PolicyContainer.MY_TEST_POLICY").apply {
                    addAllowedScopes(PolicyMetadata.PolicyScope.POLICY_SCOPE_USER)
                    affectedResource = PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                }
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_POLICY"),
            code =
                """
                  policies.add(new StringPolicyMetadata(
                      /* id= */ MY_TEST_POLICY,
                      /* allowedScopes= */ Set.of(
                          1
                      ),
                      /* affectedResource= */ 1,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of(),
                      /* resolutionMechanism= */ null,
                      /* emptyStringAllowed= */ false,
                      /* unprintableCharactersAllowed= */ false,
                      /* maxLength= */ Integer.MAX_VALUE
                  ));
                """,
        )
    }

    @Test
    fun test_resolutionMechanismNotCoexistable_outputMatches() {
        val javaFile =
            Generator.generate(
                stringTestPolicy("test.package.MY_TEST_POLICY").apply {
                    typeSpecificMetadataBuilder.stringMetadataBuilder.resolutionMechanismBuilder
                        .notCoexistable = true
                }
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.MY_TEST_POLICY"),
            code =
                """
                  policies.add(new StringPolicyMetadata(
                      /* id= */ MY_TEST_POLICY,
                      /* allowedScopes= */ Set.of(),
                      /* affectedResource= */ 0,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of(),
                      /* resolutionMechanism= */ new ResolutionMechanismMetadata.NotCoexistable(),
                      /* emptyStringAllowed= */ false,
                      /* unprintableCharactersAllowed= */ false,
                      /* maxLength= */ Integer.MAX_VALUE
                  ));
                """,
        )
    }

    @Test
    fun test_withMaxLength_outputMatches() {
        val javaFile =
            Generator.generate(
                stringTestPolicy("test.package.PolicyContainer.MY_TEST_STRING_POLICY").apply {
                    typeSpecificMetadataBuilder.stringMetadataBuilder.maxLength = 10
                }
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_STRING_POLICY"),
            code =
                """
                    policies.add(new StringPolicyMetadata(
                        /* id= */ MY_TEST_STRING_POLICY,
                        /* allowedScopes= */ Set.of(),
                        /* affectedResource= */ 0,
                        /* requiredPermission= */ null,
                        /* requiredCrossUserPermission= */ null,
                        /* allowedDpcTypes= */ Set.of(),
                        /* resolutionMechanism= */ null,
                        /* emptyStringAllowed= */ false,
                        /* unprintableCharactersAllowed= */ false,
                        /* maxLength= */ 10
                    ));
                """,
        )
    }
}

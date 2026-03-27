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

import android.processor.devicepolicy.protos.PolicyMetadataList
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.StringPolicyMetadata
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringProcessorTest {

    private val compiler = PolicyIdentifierCompiler()

    companion object {
        const val ALLOWED_DPC_TYPES_SNIPPET = PolicyIdentifierCompiler.ALLOWED_DPC_TYPES_SNIPPET
    }

    @Test
    fun test_missingResolutionMechanism_failsToCompile() {
        compiler.compileExpectError(
            """
                /**
                * ResolutionMechanism can not be empty.
                */
                @StringPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        ),
                        resolutionMechanism = @StringResolutionMechanism()
                )
                public static final PolicyIdentifier<String> POLICY_KEY =
                    new PolicyIdentifier<>("POLICY_KEY");
            """,
            expectedError = "Resolution mechanism can not be empty",
        )
    }

    @Test
    fun test_twoResolutionMechanisms_failsToCompile() {
        compiler.compileExpectError(
            """
                /**
                * ResolutionMechanism can not have multiple mechanisms.
                */
                @StringPolicyDefinition(
                        base = @PolicyDefinition(
                                allowedScopes = { POLICY_SCOPE_USER },
                                affectedResource = RESOURCE_DEVICE_WIDE,
                                $ALLOWED_DPC_TYPES_SNIPPET
                        ),
                        resolutionMechanism = @StringResolutionMechanism(
                            custom = true,
                            notCoexistable = true
                        )
                )
                public static final PolicyIdentifier<String> POLICY_KEY =
                    new PolicyIdentifier<>("POLICY_KEY");
            """,
            expectedError = "Only one resolution mechanism can be selected",
        )
    }

    @Test
    fun test_resolutionMechanism_custom_addedToTextProto() {
        val generatedResolutionMechanism =
            compiler
                .compileExpectSuccess(
                    """
                        /** * ResolutionMechanism custom.  */
                        @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                        allowedScopes = { POLICY_SCOPE_USER },
                                        affectedResource = RESOURCE_DEVICE_WIDE,
                                        $ALLOWED_DPC_TYPES_SNIPPET
                                ),
                                resolutionMechanism = @StringResolutionMechanism(
                                    custom = true
                                )
                        )
                        public static final PolicyIdentifier<String> POLICY_KEY =
                            new PolicyIdentifier<>("POLICY_KEY");
                    """
                )
                .getResolutionMechanism()

        val expectedResolutionMechanism =
            StringPolicyMetadata.ResolutionMechanism.newBuilder().setCustom(true).build()

        assertThat(generatedResolutionMechanism).isEqualTo(expectedResolutionMechanism)
    }

    @Test
    fun test_resolutionMechanism_notCoexistable_addedToTextProto() {
        val generatedResolutionMechanism =
            compiler
                .compileExpectSuccess(
                    """
                        /** * ResolutionMechanism not-coexistable.  */
                        @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                        allowedScopes = { POLICY_SCOPE_USER },
                                        affectedResource = RESOURCE_DEVICE_WIDE,
                                        $ALLOWED_DPC_TYPES_SNIPPET
                                ),
                                resolutionMechanism = @StringResolutionMechanism(
                                    notCoexistable = true
                                )
                        )
                        public static final PolicyIdentifier<String> POLICY_KEY =
                            new PolicyIdentifier<>("POLICY_KEY");
                    """
                )
                .getResolutionMechanism()

        val expectedResolutionMechanism =
            StringPolicyMetadata.ResolutionMechanism.newBuilder().setNotCoexistable(true).build()

        assertThat(generatedResolutionMechanism).isEqualTo(expectedResolutionMechanism)
    }

    private fun PolicyMetadataList.getResolutionMechanism():
        StringPolicyMetadata.ResolutionMechanism? =
        this.getPolicyMetadataList()
            ?.getOrNull(0)
            ?.getTypeSpecificMetadata()
            ?.getStringMetadata()
            ?.getResolutionMechanism()
}

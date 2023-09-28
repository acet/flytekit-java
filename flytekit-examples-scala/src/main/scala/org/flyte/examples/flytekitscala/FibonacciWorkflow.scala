/*
 * Copyright 2020-2023 Flyte Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.flyte.examples.flytekitscala

import org.flyte.flytekit.SdkBindingData
import org.flyte.flytekitscala.{
  Description,
  SdkLiteralTypes,
  SdkScalaType,
  SdkScalaWorkflow,
  SdkScalaWorkflowBuilder
}

case class FibonacciWorkflowInput(
    @Description("Value for fib0")
    fib0: SdkBindingData[Long],
    @Description("Value for fib1")
    fib1: SdkBindingData[Long]
)

class FibonacciWorkflow
    extends SdkScalaWorkflow[FibonacciWorkflowInput, SdkBindingData[Long]](
      SdkScalaType[FibonacciWorkflowInput],
      SdkScalaType(
        SdkLiteralTypes.integers(),
        "fib5",
        "Computed value for fib5"
      )
    ) {

  override def expand(
      builder: SdkScalaWorkflowBuilder,
      input: FibonacciWorkflowInput
  ): SdkBindingData[Long] = {

    val fib2 = builder
      .apply("fib-2", new SumTask(), SumTaskInput(input.fib0, input.fib1))
      .getOutputs
    val fib3 = builder
      .apply("fib-3", new SumTask(), SumTaskInput(input.fib1, fib2))
      .getOutputs
    val fib4 = builder
      .apply("fib-4", new SumTask(), SumTaskInput(fib2, fib3))
      .getOutputs
    val fib5 = builder
      .apply("fib-5", new SumTask(), SumTaskInput(fib3, fib4))
      .getOutputs

    fib5
  }
}

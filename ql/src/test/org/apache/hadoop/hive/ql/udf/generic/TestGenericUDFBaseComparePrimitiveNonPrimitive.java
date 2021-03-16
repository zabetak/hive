/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.ql.udf.generic;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.JavaIntObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.JavaStringObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory.getStandardListObjectInspector;
import static org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory.getStandardMapObjectInspector;
import static org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory.getStandardStructObjectInspector;
import static org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory.getStandardUnionObjectInspector;

@RunWith(Parameterized.class)
public class TestGenericUDFBaseComparePrimitiveNonPrimitive {
  
  private final BinaryUDFCall call;

  public TestGenericUDFBaseComparePrimitiveNonPrimitive(BinaryUDFCall call) {
    this.call = call;
  }

  @Parameterized.Parameters
  public static Collection<BinaryUDFCall> inputData() {
    List<BinaryUDFCall> data = new ArrayList<>();

    JavaIntObjectInspector intI = PrimitiveObjectInspectorFactory.javaIntObjectInspector;
    JavaStringObjectInspector stringI = PrimitiveObjectInspectorFactory.javaStringObjectInspector;

    List<ObjectInspector> nonPrimitives = new ArrayList<>();
    nonPrimitives.add(getStandardListObjectInspector(intI));
    nonPrimitives.add(getStandardMapObjectInspector(intI, stringI));
    nonPrimitives.add(getStandardStructObjectInspector(Arrays.asList("strField", "intField"), Arrays.asList(stringI, intI)));
    nonPrimitives.add(getStandardUnionObjectInspector(Arrays.asList(stringI, intI)));

    for (PrimitiveObjectInspector.PrimitiveCategory l : PrimitiveObjectInspector.PrimitiveCategory.values()) {
      if (l == PrimitiveObjectInspector.PrimitiveCategory.UNKNOWN) {
        continue;
      }
      ObjectInspector firstArgInspector = PrimitiveObjectInspectorFactory.getPrimitiveJavaObjectInspector(l);
      for (ObjectInspector secondArgInspector : nonPrimitives) {
        List<GenericUDF> udfs = Arrays.asList(
            new GenericUDFOPEqual(),
            new GenericUDFOPGreaterThan(),
            new GenericUDFOPLessThan(),
            new GenericUDFOPEqualOrGreaterThan(),
            new GenericUDFOPEqualOrLessThan(),
            new GenericUDFOPEqualNS(),
            new GenericUDFOPNotEqual(),
            new GenericUDFOPEqualNS());
        for (GenericUDF udf : udfs) {
          data.add(new BinaryUDFCall(udf, firstArgInspector, secondArgInspector));
          data.add(new BinaryUDFCall(udf, secondArgInspector, firstArgInspector));
        }
      }
    }
    return data;
  }

  @Test
  public void testInitialiseThrowsException() {
    try {
      call.udf.initialize(new ObjectInspector[] { call.left, call.right });
      Assert.fail("Comparing types from different categories should fail");
    } catch (UDFArgumentException e) {
      Assert.assertTrue(e.getMessage().contains("Type mismatch"));
    }
  }

}

/*
 *  Copyright © 2017-2019 Cask Data, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package io.cdap.wrangler.parser;

import io.cdap.wrangler.TestingRig;
import io.cdap.wrangler.api.CompileStatus;
import io.cdap.wrangler.api.Compiler;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.RecipeParser;
import io.cdap.wrangler.api.DirectiveParseException;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Tests {@link GrammarBasedParser}
 */
public class GrammarBasedParserTest {

  @Test
  public void testBasic() throws Exception {
    String[] recipe = new String[] {
      "#pragma version 2.0;",
      "rename :col1 :col2",
      "parse-as-csv :body ',' true;",
      "#pragma load-directives text-reverse, text-exchange;",
      "${macro} ${macro_2}",
      "${macro_${test}}"
    };

    RecipeParser parser = TestingRig.parse(recipe);
    List<Directive> directives = parser.parse();
    Assert.assertEquals(2, directives.size());
  }

  @Test
  public void testLoadableDirectives() throws Exception {
    String[] recipe = new String[] {
      "#pragma version 2.0;",
      "#pragma load-directives text-reverse, text-exchange;",
      "rename col1 col2",
      "parse-as-csv body , true",
      "text-reverse :body;",
      "test prop: { a='b', b=1.0, c=true};",
      "#pragma load-directives test-change,text-exchange, test1,test2,test3,test4;"
    };

    Compiler compiler = new RecipeCompiler();
    CompileStatus status = compiler.compile(new MigrateToV2(recipe).migrate());
    Assert.assertEquals(7, status.getSymbols().getLoadableDirectives().size());
  }

  @Test
  public void testCommentOnlyRecipe() throws Exception {
    String[] recipe = new String[] {
      "// test"
    };

    RecipeParser parser = TestingRig.parse(recipe);
    List<Directive> directives = parser.parse();
    Assert.assertEquals(0, directives.size());
  }

  @Test
  public void testByteSizeAndTimeDurationParsing() throws Exception {
    // Test valid byte size and time duration in recipes
    String recipe = "aggregate size_column:bytes time_column:duration total_size_column:total_bytes total_time_column:total_time size_unit:MB time_unit:minutes";
    RecipeParser parser = TestingRig.parse(recipe);
    List<Directive> directives = parser.parse();
    Assert.assertNotNull(directives);
    
    // Test with different units
    recipe = "aggregate size_column:bytes time_column:duration total_size_column:total_bytes total_time_column:total_time size_unit:GB time_unit:hours";
    parser = TestingRig.parse(recipe);
    directives = parser.parse();
    Assert.assertNotNull(directives);
    
    // Test with average aggregation
    recipe = "aggregate size_column:bytes time_column:duration total_size_column:total_bytes total_time_column:total_time time_aggregation:average";
    parser = TestingRig.parse(recipe);
    directives = parser.parse();
    Assert.assertNotNull(directives);
    
    // Test invalid byte size
    String invalidByteSize = "aggregate size_column:invalid_bytes time_column:duration total_size_column:total_bytes total_time_column:total_time";
    try {
      TestingRig.parse(invalidByteSize).parse();
      Assert.fail("Expected DirectiveParseException for invalid byte size");
    } catch (DirectiveParseException e) {
      // Expected
    }
    
    // Test invalid time duration
    String invalidTime = "aggregate size_column:bytes time_column:invalid_time total_size_column:total_bytes total_time_column:total_time";
    try {
      TestingRig.parse(invalidTime).parse();
      Assert.fail("Expected DirectiveParseException for invalid time duration");
    } catch (DirectiveParseException e) {
      // Expected
    }
    
    // Test invalid unit
    String invalidUnit = "aggregate size_column:bytes time_column:duration total_size_column:total_bytes total_time_column:total_time size_unit:invalid";
    try {
      TestingRig.parse(invalidUnit).parse();
      Assert.fail("Expected DirectiveParseException for invalid unit");
    } catch (DirectiveParseException e) {
      // Expected
    }
  }

}

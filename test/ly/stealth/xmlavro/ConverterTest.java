/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ly.stealth.xmlavro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static junit.framework.Assert.*;

public class ConverterTest {
    @Test
    public void basic() {
        String xsd = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                     "  <xs:element name='root' type='xs:string'/>" +
                     "</xs:schema>";

        Converter.createSchema(xsd);

        try { // no namespace
            Converter.createSchema("<schema/>");
            fail();
        } catch (ConverterException e) {
            String message = e.getMessage();
            assertTrue(message, message.contains("http://www.w3.org/2001/XMLSchema"));
            assertTrue(message, message.contains("namespace"));
        }
    }

    @Test
    public void rootPrimitive() {
        String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                "   <xs:element name='i' type='xs:int'/>" +
                "</xs:schema>";

        Schema schema = Converter.createSchema(xsd);
        assertEquals(Schema.Type.INT, schema.getType());

        String xml = "<i>1</i>";
        assertEquals(1, Converter.createDatum(schema, xml));
    }

    @Test
    public void severalRoots() {
        String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                "   <xs:element name='i' type='xs:int'/>" +
                "   <xs:element name='r'>" +
                "     <xs:complexType>" +
                "       <xs:sequence>" +
                "         <xs:element name='s' type='xs:string'/>" +
                "       </xs:sequence>" +
                "     </xs:complexType>" +
                "   </xs:element>" +
                "</xs:schema>";

        Schema schema = Converter.createSchema(xsd);
        assertEquals(Schema.Type.RECORD, schema.getType());
        assertEquals(Source.DOCUMENT, schema.getProp(Source.SOURCE));
        assertEquals(2, schema.getFields().size());

        Schema.Field field0 = schema.getFields().get(0);
        assertEquals("" + new Source("i"), field0.getProp(Source.SOURCE));
        assertEquals(Schema.Type.UNION, field0.schema().getType());
        assertEquals(Schema.Type.INT, field0.schema().getTypes().get(0).getType());
        assertEquals(Schema.Type.NULL, field0.schema().getTypes().get(1).getType());

        Schema.Field field1 = schema.getFields().get(1);
        assertEquals("" + new Source("r"), field1.getProp(Source.SOURCE));
        assertEquals(Schema.Type.UNION, field1.schema().getType());
        assertEquals(Schema.Type.RECORD, field1.schema().getTypes().get(0).getType());
        assertEquals(Schema.Type.NULL, field1.schema().getTypes().get(1).getType());

        String xml = "<i>5</i>";
        GenericData.Record record = Converter.createDatum(schema, xml);
        assertEquals(null, record.get("r"));
        assertEquals(5, record.get("i"));

        xml = "<r><s>s</s></r>";
        record = Converter.createDatum(schema, xml);
        GenericData.Record subRecord = (GenericData.Record) record.get("r");
        assertEquals("s", subRecord.get("s"));
    }

    @Test
    public void rootRecord() {
        String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                "   <xs:element name='root'>" +
                "     <xs:complexType>" +
                "       <xs:sequence>" +
                "         <xs:element name='i' type='xs:int'/>" +
                "         <xs:element name='s' type='xs:string'/>" +
                "         <xs:element name='d' type='xs:double'/>" +
                "       </xs:sequence>" +
                "     </xs:complexType>" +
                "   </xs:element>" +
                "</xs:schema>";

        Schema schema = Converter.createSchema(xsd);
        assertEquals(Schema.Type.RECORD, schema.getType());
        assertEquals("type0", schema.getName());
        assertEquals(3, schema.getFields().size());

        assertEquals(Schema.Type.INT, schema.getField("i").schema().getType());
        assertEquals(Schema.Type.STRING, schema.getField("s").schema().getType());
        assertEquals(Schema.Type.DOUBLE, schema.getField("d").schema().getType());

        String xml =
                "<root>" +
                "  <i>1</i>" +
                "  <s>s</s>" +
                "  <d>1.0</d>" +
                "</root>";

        GenericData.Record record = Converter.createDatum(schema, xml);

        assertEquals(1, record.get("i"));
        assertEquals("s", record.get("s"));
        assertEquals(1.0, record.get("d"));
    }

    @Test
    public void nestedRecursiveRecords() {
        String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                "  <xs:complexType name='type'>" +
                "    <xs:sequence>" +
                "      <xs:element name='node' type='type' minOccurs='0'/>" +
                "    </xs:sequence>" +
                "  </xs:complexType>" +
                "  <xs:element name='root' type='type'/>" +
                "</xs:schema>";

        Schema schema = Converter.createSchema(xsd);

        Schema.Field field = schema.getField("node");
        Schema subSchema = field.schema();
        assertSame(schema, subSchema.getTypes().get(0));

        String xml = "<root><node></node></root>";
        GenericData.Record record = Converter.createDatum(schema, xml);

        GenericData.Record child = (GenericData.Record) record.get("node");
        assertEquals(record.getSchema(), child.getSchema());

        assertNull(child.get("node"));
    }

    @Test
    public void attributes() {
        String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                "  <xs:element name='root'>" +
                "    <xs:complexType>" +
                "      <xs:attribute name='required' use='required'/>" +
                "      <xs:attribute name='prohibited' use='prohibited'/>" +
                "      <xs:attribute name='optional' use='optional'/>" +
                "    </xs:complexType>" +
                "  </xs:element>" +
                "</xs:schema>";

        Schema schema = Converter.createSchema(xsd);

        Schema.Field required = schema.getField("required");
        assertEquals(Schema.Type.STRING, required.schema().getType());

        assertNull(schema.getField("prohibited"));

        Schema.Field optional = schema.getField("optional");
        assertEquals(Schema.Type.UNION, optional.schema().getType());
        assertEquals(
                Arrays.asList(Schema.create(Schema.Type.STRING), Schema.create(Schema.Type.NULL)),
                optional.schema().getTypes()
        );

        String xml = "<root required='required' optional='optional'/>";
        GenericData.Record record = Converter.createDatum(schema, xml);
        assertEquals("required", record.get("required"));
        assertEquals("optional", record.get("optional"));

        xml = "<root required='required'/>";
        record = Converter.createDatum(schema, xml);
        assertEquals("required", record.get("required"));
        assertNull(record.get("optional"));
    }

    @Test
    public void uniqueFieldNames() {
        String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                "  <xs:complexType name='type'>" +
                "    <xs:sequence>" +
                "      <xs:element name='field' type='xs:string'/>" +
                "    </xs:sequence>" +
                "    <xs:attribute name='field' type='xs:string'/>" +
                "  </xs:complexType>" +
                "  <xs:element name='root' type='type'/>" +
                "</xs:schema>";

        Schema schema = Converter.createSchema(xsd);

        assertEquals(2, schema.getFields().size());
        Schema.Field field = schema.getField("field");
        assertNotNull(field);
        assertEquals("" + new Source("field", true), field.getProp(Source.SOURCE));

        Schema.Field field0 = schema.getField("field0");
        assertEquals("" + new Source("field", false), field0.getProp(Source.SOURCE));

        String xml = "<root field='value'><field>value0</field></root>";
        GenericData.Record record = Converter.createDatum(schema, xml);

        assertEquals("value", record.get("field"));
        assertEquals("value0", record.get("field0"));
    }

    @Test
    public void recordWithWildcardField() {
        String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                "  <xs:complexType name='type'>" +
                "    <xs:sequence>" +
                "      <xs:element name='field' type='xs:string'/>" +
                "      <xs:any/>" +
                "    </xs:sequence>" +
                "  </xs:complexType>" +
                "  <xs:element name='root' type='type'/>" +
                "</xs:schema>";

        Schema schema = Converter.createSchema(xsd);
        assertEquals(2, schema.getFields().size());

        Schema.Field wildcardField = schema.getField(Source.WILDCARD);
        assertEquals(Schema.Type.MAP, wildcardField.schema().getType());

        // Two wildcard-matched elements
        String xml =
                "<root>" +
                "  <field>field</field>" +
                "  <field0>field0</field0>" +
                "  <field1>field1</field1>" +
                "</root>";

        GenericData.Record record = Converter.createDatum(schema, xml);
        assertEquals("field", record.get("field"));

        @SuppressWarnings("unchecked")
        java.util.Map<String, String> map = (java.util.Map<String, String>) record.get(Source.WILDCARD);

        assertEquals(2, map.size());
        assertEquals("field0", map.get("field0"));
        assertEquals("field1", map.get("field1"));

        // No wildcard-matched element
        xml = "<root><field>field</field></root>";
        record = Converter.createDatum(schema, xml);

        assertEquals("field", record.get("field"));
        assertEquals(Collections.emptyMap(), record.get(Source.WILDCARD));
    }

    @Test
    public void severalWildcards() {
        String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                "  <xs:element name='root'>" +
                "    <xs:complexType>" +
                "      <xs:sequence>" +
                "        <xs:any/>" +
                "        <xs:any/>" +
                "      </xs:sequence>" +
                "    </xs:complexType>" +
                "  </xs:element>" +
                "</xs:schema>";

        Schema schema = Converter.createSchema(xsd);
        assertEquals(1, schema.getFields().size());

        Schema.Field field = schema.getField(Source.WILDCARD);
        assertEquals(null, field.getProp(Source.SOURCE));
    }

    @Test
    public void optionalElementValues() {
        String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                "  <xs:element name='root'>" +
                "    <xs:complexType>" +
                "      <xs:sequence>" +
                "        <xs:element name='required' type='xs:string'/>" +
                "        <xs:element name='optional' type='xs:string' minOccurs='0'/>" +
                "      </xs:sequence>" +
                "    </xs:complexType>" +
                "  </xs:element>" +
                "</xs:schema>";

        Schema schema = Converter.createSchema(xsd);
        assertEquals(2, schema.getFields().size());

        Schema.Field requiredField = schema.getField("required");
        assertEquals(Schema.Type.STRING, requiredField.schema().getType());

        Schema.Field optionalField = schema.getField("optional");
        Schema optionalSchema = optionalField.schema();
        assertEquals(Schema.Type.UNION, optionalSchema.getType());

        assertEquals(
                Arrays.asList(Schema.create(Schema.Type.STRING), Schema.create(Schema.Type.NULL)),
                optionalSchema.getTypes()
        );

        String xml = "<root><required>required</required></root>";
        GenericData.Record record = Converter.createDatum(schema, xml);

        assertEquals("required", record.get("required"));
        assertNull(record.get("optional"));

        xml = "<root>" +
              "  <required>required</required>" +
              "  <optional>optional</optional>" +
              "</root>";

        record = Converter.createDatum(schema, xml);
        assertEquals("optional", record.get("optional"));
    }

    @Test
    public void array() {
        String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                "  <xs:element name='root'>" +
                "    <xs:complexType>" +
                "      <xs:sequence>" +
                "        <xs:element name='value' type='xs:string' maxOccurs='unbounded'/>" +
                "      </xs:sequence>" +
                "    </xs:complexType>" +
                "  </xs:element>" +
                "</xs:schema>";

        Schema schema = Converter.createSchema(xsd);
        Schema.Field valueField = schema.getField("value");
        assertEquals(Schema.Type.ARRAY, valueField.schema().getType());
        assertEquals(Schema.Type.STRING, valueField.schema().getElementType().getType());

        String xml = "<root>" +
                     "  <value>1</value>" +
                     "  <value>2</value>" +
                     "  <value>3</value>" +
                     "</root>";

        GenericData.Record record = Converter.createDatum(schema, xml);
        assertEquals(Arrays.asList("1", "2", "3"), record.get("value"));
    }

    @Test
    public void choiceElements() {
        String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                "  <xs:element name='root'>" +
                "    <xs:complexType>" +
                "      <xs:choice>" +
                "        <xs:element name='s' type='xs:string'/>" +
                "        <xs:element name='i' type='xs:int'/>" +
                "      </xs:choice>" +
                "    </xs:complexType>" +
                "  </xs:element>" +
                "</xs:schema>";

        Schema schema = Converter.createSchema(xsd);
        assertEquals(Schema.Type.RECORD, schema.getType());
        assertEquals(2, schema.getFields().size());

        Schema.Field sField = schema.getField("s");
        assertEquals(Schema.Type.UNION, sField.schema().getType());
        assertEquals(
                Arrays.asList(Schema.create(Schema.Type.STRING), Schema.create(Schema.Type.NULL)),
                sField.schema().getTypes()
        );

        Schema.Field iField = schema.getField("i");
        assertEquals(Schema.Type.UNION, iField.schema().getType());
        assertEquals(
                Arrays.asList(Schema.create(Schema.Type.INT), Schema.create(Schema.Type.NULL)),
                iField.schema().getTypes()
        );

        String xml = "<root><s>s</s></root>";
        GenericData.Record record = Converter.createDatum(schema, xml);
        assertEquals("s", record.get("s"));

        xml = "<root><i>1</i></root>";
        record = Converter.createDatum(schema, xml);
        assertEquals(1, record.get("i"));
    }

    @Test
    public void SchemaBuilder_validName() {
        SchemaBuilder builder = new SchemaBuilder();

        assertNull(builder.validName(null));
        assertEquals("", builder.validName(""));

        assertEquals("a1", builder.validName("$a#1"));

        assertEquals("a_1", builder.validName("a.1"));
        assertEquals("a_1", builder.validName("a-1"));

        // built-in types
        assertEquals("string0", builder.validName("string"));
        assertEquals("record1", builder.validName("record"));
    }
}

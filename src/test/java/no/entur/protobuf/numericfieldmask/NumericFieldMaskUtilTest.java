package no.entur.protobuf.numericfieldmask;

/*-
 * #%L
 * Numeric field mask for protobuf
 * %%
 * Copyright (C) 2022 Entur
 * %%
 * Licensed under the EUPL, Version 1.1 or – as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 * 
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * http://ec.europa.eu/idabc/eupl5
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;

import no.entur.protobuf.NumericFieldMask;

public class NumericFieldMaskUtilTest {

	@Test
	public void testToFieldMaskSingleLevel() {
		FieldMask allFields = NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(), NumericFieldMask.newBuilder().setInvertMask(true).build());
		assertContainsExact(allFields, "seconds", "nanos");

		FieldMask onlySeconds = NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(),
				NumericFieldMask.newBuilder()
						.setInvertMask(true)
						.addFieldNumberPath(NumericFieldMaskUtil.buildNestedPath(Timestamp.NANOS_FIELD_NUMBER))
						.build());
		assertContainsExact(onlySeconds, "seconds");

		FieldMask onlyNano = NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(),
				NumericFieldMask.newBuilder()
						.setInvertMask(false)
						.addFieldNumberPath(NumericFieldMaskUtil.buildNestedPath(Timestamp.NANOS_FIELD_NUMBER))
						.build());
		assertContainsExact(onlyNano, "nanos");
	}

	@Test
	public void testToFieldMaskNested() {
		FieldMask sourceContextSubField = NumericFieldMaskUtil.toFieldMask(com.google.protobuf.Type.getDescriptor(),
				NumericFieldMask.newBuilder().addFieldNumberPath("4.1").addFieldNumberPath("5.1").build());
		assertContainsExact(sourceContextSubField, "options.name", "source_context.file_name");

	}

	@Test
	public void testToFieldMaskNestedInverted_thenRemoveParent() {
		FieldMask sourceContextSubFieldInverted = NumericFieldMaskUtil.toFieldMask(com.google.protobuf.Type.getDescriptor(),
				NumericFieldMask.newBuilder().addFieldNumberPath("5.1").addFieldNumberPath("4.1").addFieldNumberPath("4.2").setInvertMask(true).build());
		assertContainsExact(sourceContextSubFieldInverted, "fields", "name", "oneofs", "syntax");
	}

	@Test
	public void testToFieldMaskNestedInverted_whenParentHasRemainingChildren_thenKeepParent() {
		FieldMask sourceContextSubFieldInverted = NumericFieldMaskUtil.toFieldMask(com.google.protobuf.Type.getDescriptor(),
				NumericFieldMask.newBuilder().addFieldNumberPath("4.1").setInvertMask(true).build());
		assertContainsExact(sourceContextSubFieldInverted, "name", "fields", "oneofs", "options.value", "source_context", "syntax");
	}

	private void assertContainsExact(FieldMask allFields, String... paths) {
		Arrays.sort(paths);
		assertEquals(paths.length, allFields.getPathsList().size());
		for (String path : paths) {
			assertTrue(allFields.getPathsList().contains(path), "Missing path " + path + ", fieldMask=" + allFields.getPathsList() + ", paths=" + paths);
		}

	}

	@Test
	public void testParseMaskToTree() {

		NumericFieldMask mask = NumericFieldMask.newBuilder()
				.addFieldNumberPath("1")
				.addFieldNumberPath("1.1")
				.addFieldNumberPath("2.2")
				.addFieldNumberPath("3.3.1")
				.build();

		NumericFieldMaskUtil.Tree<Integer> tree = NumericFieldMaskUtil.buildMaskTree(mask);

		assertEquals(3, tree.rootNode.children.size());
		assertEquals(tree.rootNode.children.get(0).children.size(), 1);
		assertEquals(tree.rootNode.children.get(0).children.get(0).value, 1);
		assertEquals(tree.rootNode.children.get(1).children.size(), 1);
		assertEquals(tree.rootNode.children.get(1).children.get(0).value, 2);
		assertEquals(tree.rootNode.children.get(2).children.size(), 1);
		assertEquals(tree.rootNode.children.get(2).children.get(0).value, 3);
		assertEquals(tree.rootNode.children.get(2).children.get(0).children.get(0).value, 1);

	}

	@Test
	public void testParseMessageToTreeSingleLevel() {

		int maxDepth = 1;

		NumericFieldMaskUtil.Tree<Integer> tree = NumericFieldMaskUtil.buildMessageTree(com.google.protobuf.Type.getDescriptor(), maxDepth);

		assertEquals(6, tree.rootNode.children.size());
		for (int i = 0; i < 6; i++) {
			assertEquals(0, tree.rootNode.children.get(i).children.size());

		}
		assertEquals(maxDepth, tree.getMaxDept());
	}

	@Test
	public void testParseMessageToTreeTwoLevel() {

		int maxDepth = 2;

		NumericFieldMaskUtil.Tree<Integer> tree = NumericFieldMaskUtil.buildMessageTree(com.google.protobuf.Type.getDescriptor(), maxDepth);

		assertEquals(6, tree.rootNode.children.size());
		assertEquals(0, tree.rootNode.children.get(0).children.size()); // name
		assertEquals(10, tree.rootNode.children.get(1).children.size()); // fields
		assertEquals(0, tree.rootNode.children.get(2).children.size()); // oneofs
		assertEquals(2, tree.rootNode.children.get(3).children.size()); // options
		assertEquals(1, tree.rootNode.children.get(4).children.size()); // source_context
		assertEquals(0, tree.rootNode.children.get(5).children.size()); // syntax

		assertEquals(maxDepth, tree.getMaxDept());
	}

	@Test
	public void testStringSorting() {
		List<String> list = new ArrayList<>();
		list.add("1");
		list.add("2");
		list.add("1.1");

		Collections.sort(list);
		assertEquals("1", list.get(0));
		assertEquals("1.1", list.get(1));
		assertEquals("2", list.get(2));
	}

	@Test
	public void testToMask() {
		NumericFieldMaskUtil.Tree<Integer> tree = new NumericFieldMaskUtil.Tree<>(-1);
		tree.rootNode.addChildPath(new ArrayList<>(List.of(1))); // Wrap in ArrayList to allow modifications
		tree.rootNode.addChildPath(new ArrayList<>(List.of(2))); // Wrap in ArrayList to allow modifications
		tree.rootNode.addChildPath(new ArrayList<>(List.of(3))); // Wrap in ArrayList to allow modifications
		NumericFieldMask numericFieldMask = tree.toMask();
		assertEquals(3, numericFieldMask.getFieldNumberPathCount());

	}

}

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Type;
import com.google.protobuf.util.FieldMaskUtil;

import no.entur.protobuf.NumericFieldMask;

class NumericFieldMaskUtilTest {

	@Test
	void testToFieldMaskSingleLevel() throws InvalidFieldMaskException {
		FieldMask allFields = NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(), NumericFieldMask.newBuilder().setInvertMask(true).build());
		assertContainsExact(allFields, "seconds", "nanos");

		FieldMask onlySeconds = NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(),
				NumericFieldMask.newBuilder()
						.setInvertMask(true)
						.addFieldNumberPath(NumericFieldMaskUtil.buildNestedPath(Timestamp.NANOS_FIELD_NUMBER))
						.build());
		assertTrue(FieldMaskUtil.isValid(Timestamp.getDescriptor(), onlySeconds));
		assertContainsExact(onlySeconds, "seconds");

		FieldMask onlyNano = NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(),
				NumericFieldMask.newBuilder()
						.setInvertMask(false)
						.addFieldNumberPath(NumericFieldMaskUtil.buildNestedPath(Timestamp.NANOS_FIELD_NUMBER))
						.build());
		assertTrue(FieldMaskUtil.isValid(Timestamp.getDescriptor(), onlyNano));
		assertContainsExact(onlyNano, "nanos");
	}

	@Test
	void testToFieldMaskNested() throws InvalidFieldMaskException {
		FieldMask sourceContextSubField = NumericFieldMaskUtil.toFieldMask(com.google.protobuf.Type.getDescriptor(),
				NumericFieldMask.newBuilder().addFieldNumberPath("5.1").build());
		assertTrue(FieldMaskUtil.isValid(com.google.protobuf.Type.getDescriptor(), sourceContextSubField));
		assertContainsExact(sourceContextSubField, "source_context.file_name");

	}

	@Test
	void testToFieldMaskNestedInverted_thenRemoveParent() throws InvalidFieldMaskException {
		FieldMask sourceContextSubFieldInverted = NumericFieldMaskUtil.toFieldMask(com.google.protobuf.Type.getDescriptor(),
				NumericFieldMask.newBuilder().addFieldNumberPath("5.1").addFieldNumberPath("4.1").addFieldNumberPath("4.2").setInvertMask(true).build());
		assertTrue(FieldMaskUtil.isValid(com.google.protobuf.Type.getDescriptor(), sourceContextSubFieldInverted));
		assertContainsExact(sourceContextSubFieldInverted, "edition", "fields", "name", "oneofs", "syntax");
	}

	private void assertContainsExact(FieldMask allFields, String... paths) {
		Arrays.sort(paths);
		assertEquals(paths.length, allFields.getPathsList().size());
		for (String path : paths) {
			assertTrue(allFields.getPathsList().contains(path), "Missing path " + path + ", fieldMask=" + allFields.getPathsList() + ", paths=" + paths);
		}

	}

	@Test
	void testParseMaskToTree() {

		NumericFieldMask mask = NumericFieldMask.newBuilder()
				.addFieldNumberPath("1")
				.addFieldNumberPath("1.1")
				.addFieldNumberPath("2.2")
				.addFieldNumberPath("3.3.1")
				.build();

		NumericFieldMaskUtil.Tree<Integer> tree = NumericFieldMaskUtil.buildMaskTree(mask);

		assertEquals(3, tree.getRootNode().getChildren().size());
		assertEquals(1, tree.getRootNode().getChildren().get(0).getChildren().size());
		assertEquals(1, tree.getRootNode().getChildren().get(0).getChildren().get(0).getValue());
		assertEquals(1, tree.getRootNode().getChildren().get(1).getChildren().size());
		assertEquals(2, tree.getRootNode().getChildren().get(1).getChildren().get(0).getValue());
		assertEquals(1, tree.getRootNode().getChildren().get(2).getChildren().size());
		assertEquals(3, tree.getRootNode().getChildren().get(2).getChildren().get(0).getValue());
		assertEquals(1, tree.getRootNode().getChildren().get(2).getChildren().get(0).getChildren().get(0).getValue());

	}

	@Test
	void testParseMessageToTreeSingleLevel() {

		int maxDepth = 1;

		NumericFieldMaskUtil.Tree<Integer> tree = NumericFieldMaskUtil.buildMessageTree(com.google.protobuf.Type.getDescriptor(), maxDepth);

		assertEquals(7, tree.getRootNode().getChildren().size());
		for (int i = 0; i < 6; i++) {
			assertEquals(0, tree.getRootNode().getChildren().get(i).getChildren().size());

		}
		assertEquals(maxDepth, tree.getMaxDept());
	}

	@Test
	void testParseMessageToTreeTwoLevel() {

		int maxDepth = 2;

		NumericFieldMaskUtil.Tree<Integer> tree = NumericFieldMaskUtil.buildMessageTree(com.google.protobuf.Type.getDescriptor(), maxDepth);

		assertEquals(7, tree.getRootNode().getChildren().size());
		assertEquals(0, tree.getRootNode().getChildren().get(0).getChildren().size()); // name
		assertEquals(10, tree.getRootNode().getChildren().get(1).getChildren().size()); // fields
		assertEquals(0, tree.getRootNode().getChildren().get(2).getChildren().size()); // oneofs
		assertEquals(2, tree.getRootNode().getChildren().get(3).getChildren().size()); // options
		assertEquals(1, tree.getRootNode().getChildren().get(4).getChildren().size()); // source_context
		assertEquals(0, tree.getRootNode().getChildren().get(5).getChildren().size()); // syntax

		assertEquals(maxDepth, tree.getMaxDept());
	}

	@Test
	void testStringSorting() {
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
	void testToMask() {
		NumericFieldMaskUtil.Tree<Integer> tree = new NumericFieldMaskUtil.Tree<>(-1);
		tree.getRootNode().addChildPath(new ArrayList<>(List.of(1))); // Wrap in ArrayList to allow modifications
		tree.getRootNode().addChildPath(new ArrayList<>(List.of(2))); // Wrap in ArrayList to allow modifications
		tree.getRootNode().addChildPath(new ArrayList<>(List.of(3))); // Wrap in ArrayList to allow modifications
		NumericFieldMask numericFieldMask = tree.toMask();
		assertEquals(3, numericFieldMask.getFieldNumberPathCount());

	}

	@Test
	public void testInvalidFieldNumbers() {
		assertThrows(InvalidFieldMaskException.class,
				() -> NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(), NumericFieldMask.newBuilder().addFieldNumberPath("3").build()));
		assertThrows(InvalidFieldMaskException.class,
				() -> NumericFieldMaskUtil.toFieldMask(Type.getDescriptor(), NumericFieldMask.newBuilder().addFieldNumberPath("3.13").build()));
	}

	@Test
	public void testInvalidSyntax() {
		assertThrows(InvalidFieldMaskException.class,
				() -> NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(), NumericFieldMask.newBuilder().addFieldNumberPath("seconds").build()));
		assertThrows(InvalidFieldMaskException.class,
				() -> NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(), NumericFieldMask.newBuilder().addFieldNumberPath("nanos").build()));
		assertThrows(InvalidFieldMaskException.class,
				() -> NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(), NumericFieldMask.newBuilder().addFieldNumberPath("seconds.nanos").build()));
		assertThrows(InvalidFieldMaskException.class,
				() -> NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(), NumericFieldMask.newBuilder().addFieldNumberPath("1.").build()));
		assertThrows(InvalidFieldMaskException.class,
				() -> NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(), NumericFieldMask.newBuilder().addFieldNumberPath("1..").build()));
	}
}

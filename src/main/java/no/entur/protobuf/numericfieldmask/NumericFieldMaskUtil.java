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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.protobuf.Descriptors;
import com.google.protobuf.FieldMask;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.util.FieldMaskUtil;

import no.entur.protobuf.NumericFieldMask;

public class NumericFieldMaskUtil {

	public static final String PATH_SEPARATOR = ".";
	public static final String PATH_SEPARATOR_REGEX = "\\.";

	/**
	 * Convert a numeric field mask to a Google Protobuf fieldmask with fieldnames instead of fieldnumbers (ie "1.2" -> "rootMessage.subMessage")
	 * 
	 * @param protoDescriptor MessageDescriptor of root message that paths refer to
	 * @param mask            mask to process
	 * @return standard fieldmask that can be used using FieldMaskUtil
	 */
	public static FieldMask toFieldMask(Descriptors.Descriptor protoDescriptor, NumericFieldMask mask) {

		FieldMask.Builder fieldMask = FieldMask.newBuilder();
		if (mask.getInvertMask()) {
			mask = invertMask(protoDescriptor, mask);
		}

		// Convert numbered field paths ie "1.2" to field names
		for (String path : mask.getFieldNumberPathList()) {
			fieldMask.addPaths(resolveNumericPath(null, path, protoDescriptor));
		}

		// Remove redundant fields etc
		return FieldMaskUtil.normalize(fieldMask.build());
	}

	private static NumericFieldMask invertMask(Descriptors.Descriptor messageDescriptor, NumericFieldMask mask) {
		NumericFieldMask.Builder invertedMask = NumericFieldMask.newBuilder();
		if (mask.getFieldNumberPathCount() == 0) {
			// Simple inverted mask, include all fields
			invertedMask.addAllFieldNumberPath(messageDescriptor.getFields().stream().map(e -> String.valueOf(e.getNumber())).collect(Collectors.toList()));
		} else {
			Tree<Integer> maskTree = buildMaskTree(mask);
			Tree<Integer> messageTree = buildMessageTree(messageDescriptor, maskTree.getMaxDept());
			Tree<Integer> invertedTree = messageTree.subtract(maskTree);
			return invertedTree.toMask();
		}

		return invertedMask.build();

	}

	private static String resolveNumericPath(String parentPath, String subPath, Descriptors.Descriptor messageDescriptor) {
		if (subPath.contains(PATH_SEPARATOR)) {
			String[] segments = subPath.split(PATH_SEPARATOR_REGEX);
			String parent = segments[0];
			String sub = subPath.substring(subPath.indexOf(PATH_SEPARATOR) + 1);
			Descriptors.FieldDescriptor fieldDescriptor = messageDescriptor.findFieldByNumber(Integer.valueOf(parent));
			if (fieldDescriptor.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
				Descriptors.Descriptor subMessageDescriptor = fieldDescriptor.getMessageType();
				String newParentPath = buildNestedPath(parentPath, parent);
				return buildNestedPath(toFieldName(messageDescriptor, Integer.parseInt(parent)), resolveNumericPath(newParentPath, sub, subMessageDescriptor));
			} else {
				// Not a message type to follow
				return buildNestedPath(toFieldName(messageDescriptor, Integer.parseInt(parent)));
			}
		} else {

			return buildNestedPath(toFieldName(messageDescriptor, Integer.parseInt(subPath)));
		}

	}

	/**
	 * Filter a message according to a NumericFieldMask
	 * 
	 * @param source        message to filter
	 * @param targetBuilder a builder for the target message (builder for source)
	 * @param mask          mask to apply
	 * @return a filtered message. If mask is empty, source object is returned
	 * @param <T> Protobuf parent message type (GeneratedMessageV3)
	 */
	public static <T extends GeneratedMessageV3> T copyRequestedFields(T source, GeneratedMessageV3.Builder targetBuilder, NumericFieldMask mask) {
		if (isAllFields(mask)) {
			// Optimization; if all fields requested return original object
			return source;
		} else {
			FieldMask requestedFieldMask = toFieldMask(source.getDescriptorForType(), mask);
			FieldMaskUtil.merge(requestedFieldMask, source, targetBuilder);
			return (T) targetBuilder.build();
		}
	}

	/**
	 * Checks if a mask indicates that all fields should be present.
	 * 
	 * @param mask
	 * @return
	 */
	public static boolean isAllFields(NumericFieldMask mask) {
		return mask.getInvertMask() && mask.getFieldNumberPathCount() == 0;
	}

	private static String toFieldName(Descriptors.Descriptor protoDescriptor, int fieldNumber) {
		return protoDescriptor.findFieldByNumber(fieldNumber).getName();
	}

	static String buildNestedPath(int... segments) {
		String path = Arrays.stream(segments).mapToObj(i -> ((Integer) i).toString()).collect(Collectors.joining(PATH_SEPARATOR));
		return path;
	}

	private static String buildNestedPath(String... segments) {
		return Stream.of(segments).filter(value -> null != value).collect(Collectors.joining(PATH_SEPARATOR));
	}

	static Tree<Integer> buildMaskTree(NumericFieldMask mask) {

		Tree<Integer> fieldTree = new Tree<>(-1);
		List<String> fieldNumberPathList = mask.getFieldNumberPathList();
		for (String path : fieldNumberPathList) {
			String[] elements = path.split(PATH_SEPARATOR_REGEX);

			fieldTree.rootNode.addChildPath(Arrays.stream(elements).mapToInt(Integer::parseInt).boxed().collect(Collectors.toList()));
		}

		return fieldTree;
	}

	static Tree<Integer> buildMessageTree(Descriptors.Descriptor messageDescriptor, int maxDepth) {
		Tree<Integer> fieldTree = new Tree<>(-1);
		parseMessage(fieldTree.rootNode, messageDescriptor, maxDepth - 1);
		return fieldTree;
	}

	private static void parseMessage(Node<Integer> parent, Descriptors.Descriptor messageDescriptor, int remainingDepth) {
		List<Descriptors.FieldDescriptor> fields = messageDescriptor.getFields();
		for (Descriptors.FieldDescriptor fieldDescriptor : fields) {
			Node<Integer> fieldNode = new Node<>(fieldDescriptor.getNumber());
			parent.children.add(fieldNode);
			if (fieldDescriptor.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
				// Iterate into submessage
				if (remainingDepth > 0) {
					parseMessage(fieldNode, fieldDescriptor.getMessageType(), remainingDepth - 1);
				}
			}
		}
	}

	static class Tree<T extends Comparable> {
		public Node<T> rootNode;

		public Tree(T rootData) {
			rootNode = new Node<>(rootData);
			rootNode.value = rootData;
			rootNode.children = new ArrayList<>();
		}

		public int getMaxDept() {
			return rootNode.getMaxDept(0);
		}

		public Tree<T> subtract(Tree<T> treeToRemove) {
			Tree<T> resultingTree = new Tree<T>(this.rootNode.value);
			copyExclusiveNodes(resultingTree.rootNode, rootNode, treeToRemove.rootNode);

			return resultingTree;
		}

		private void copyExclusiveNodes(Node<T> targetNode, Node<T> existingNode, Node<T> nodeToRemove) {
			for (Node<T> child : existingNode.children) {
				Optional<Node<T>> optionalNodeToRemove = nodeToRemove.children.stream().filter(e -> child.value.equals((e.value))).findFirst();
				if (optionalNodeToRemove.isEmpty()) {
					targetNode.addChild(new Node<>(child.value)); // Add without children
				} else if (!child.equals(optionalNodeToRemove.get())) {
					// Only if subtrees are different
					Node<T> newTargetNode = new Node<>(child.value);
					targetNode.addChild(newTargetNode);
					copyExclusiveNodes(newTargetNode, child, optionalNodeToRemove.get());
				}

			}

		}

		public NumericFieldMask toMask() {
			NumericFieldMask.Builder maskBuilder = NumericFieldMask.newBuilder();
			for (Node<T> child : rootNode.children) {
				// Start by list of children as root node has no meaning
				buildPath(null, child, maskBuilder);
			}

			return maskBuilder.build();
		}

		private void buildPath(String parentPath, Node<T> node, NumericFieldMask.Builder mask) {
			if (node.children.isEmpty()) {
				mask.addFieldNumberPath(buildNestedPath(parentPath, node.value + ""));
			} else {
				for (Node<T> child : node.children) {
					String path = buildNestedPath(parentPath, node.value + "");
					buildPath(path, child, mask); // Recurse further into structure
				}
			}

		}

		@Override
		public String toString() {
			return "Tree{" + "rootNode=" + rootNode + '}';
		}
	}

	static class Node<T extends Comparable> {
		public T value;
		public List<Node<T>> children = new ArrayList<>();

		public Node(T value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Node<?> node = (Node<?>) o;
			return value.equals(node.value) && children.equals(node.children);
		}

		@Override
		public int hashCode() {
			return Objects.hash(value, children);
		}

		private int getMaxDept(int currentDept) {
			return children.stream().map(e -> e.getMaxDept(currentDept + 1)).mapToInt(v -> v).max().orElse(currentDept);
		}

		public void addChildPath(List<T> pathSegments) {
			if (pathSegments.isEmpty()) {
				return;
			}
			Optional<Node<T>> optionalNode = children.stream().filter(e -> e.value.equals(pathSegments.get(0))).findFirst();
			Node<T> child;
			if (optionalNode.isPresent()) {
				child = optionalNode.get();
			} else {
				child = new Node<>(pathSegments.get(0));
				children.add(child);
			}

			List<T> remainingValues = new ArrayList<T>(pathSegments);
			remainingValues.remove(0); // Processed here
			child.addChildPath(remainingValues);

			Collections.sort(children, Comparator.comparing(e -> e.value));
		}

		public void addChild(Node<T> newTargetNode) {
			children.add(newTargetNode);
		}

		@Override
		public String toString() {
			return "Node{" + "children=" + children + ", value=" + value + '}';
		}
	}

}

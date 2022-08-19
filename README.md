# Numeric Protobuf Fieldmasks

TLDR: Describe [FieldMasks](https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/FieldMask.html) using field*numbers* instead of field*names* to allow field renaming refactoring.


Why should I use FieldMasks?
* https://netflixtechblog.com/practical-api-design-at-netflix-part-1-using-protobuf-fieldmask-35cfdc606518
* https://netflixtechblog.com/practical-api-design-at-netflix-part-2-protobuf-fieldmask-for-mutation-operations-2e75e1d230e4

Standard FieldMask operates on field*names*. By utilizing them you also quickly loose one of the nice
refactoring features of protobuf - being able to change fieldnames without breaking backwards compatibility.
This is however only valid as long as you only use the binary encoding of protobuf messages, not JSON.

Please read https://stackoverflow.com/questions/69067689/why-does-protobufs-fieldmask-use-field-names-instead-of-field-numbers to understand more.

## Functionality

* Convert [NumericFieldMask](src/main/proto/numericfieldmask.proto) to [FieldMasks](https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/FieldMask.html) to utilize functionality provided by [FieldMaskUtil](https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/util/FieldMaskUtil) 
* Support for inverting masks, ie. specify fields to exclude instead of including (uses compiled protobuf descriptors to analyze message structures)

Use standard [FieldMaskUtil](https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/util/FieldMaskUtil) operations to do actual masking operations.

Proto definition:
```protobuf
// Numeric field mask representing which top level fields should be returned by the server.
// Mandatory: Create this field mask using field *numbers* from the proto descriptor 
// in the generated code, not the field *name*
message NumericFieldMask {
  // Field number in message. Nested numbers allowed, use . to concatenate. 
  // Ie "1.2" will include the nested field number 2 in root message field 1
  repeated string field_number_path = 1;
  // Invert listed field paths instead of explicitly include them
  bool invert_mask = 2;
}
```

Example single level mask:

```java
FieldMask onlySeconds = NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(),
        NumericFieldMask.newBuilder()
        .setInvertMask(true) // Invert mask
        .addFieldNumberPath(
          NumericFieldMaskUtil.buildNestedPath(Timestamp.NANOS_FIELD_NUMBER)) // Add field to include/exclude
        .build());
```

Example multilevel level mask:

```java
FieldMask onlySeconds = NumericFieldMaskUtil.toFieldMask(Timestamp.getDescriptor(),
    NumericFieldMask.newBuilder()
        .addFieldNumberPath(NumericFieldMaskUtil.buildNestedPath(level1_fieldNumber, 
          level2_fieldNumber, level3_fieldNumber ...)) 
        .build());
```

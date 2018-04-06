package xyz.upperlevel.uppercore.config;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.*;
import xyz.upperlevel.uppercore.config.exceptions.*;
import xyz.upperlevel.uppercore.placeholder.PlaceholderUtil;
import xyz.upperlevel.uppercore.placeholder.PlaceholderValue;
import xyz.upperlevel.uppercore.util.TextUtil;

import java.io.Reader;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ConfigParser<T> {
    private static final SafeConstructor.ConstructYamlTimestamp TIMESTAMP_CONSTRUCTOR = new SafeConstructor.ConstructYamlTimestamp();
    private static Map<Class<?>, ConfigParser> parsersByClass = new HashMap<>();
    public static Yaml defaultYaml = new Yaml();

    private final Class<?> declaredClass;
    private ObjectConstructor<T> targetConstructor;
    private final boolean raw;
    private final boolean inlineable;
    private final Map<String, Property> nodesByName;
    private final List<Property> positionalArguments;

    static {
        createFrom(new StandardExternalDeclarator());
    }

    public ConfigParser(Class<?> declaredClass, Parameter[] parameters, ObjectConstructor<T> constructor, boolean inlineable) {
        this.declaredClass = declaredClass;
        this.inlineable = inlineable;
        targetConstructor = constructor;
        if (parameters.length == 1 && parameters[0].getType() == Node.class) {
            // Raw constructor (special case)
            // The constructor will manually parse the node
            raw = true;
            nodesByName = null;
            positionalArguments = null;
            return;
        }
        raw = false;
        nodesByName = new HashMap<>();
        positionalArguments = new ArrayList<>(parameters.length);
        // Parse arguments
        for (Parameter parameter : parameters) {
            Property property = new Property(parameter);
            positionalArguments.add(property);
            if (nodesByName.put(property.name, property) != null) {
                // The constructor class may be different (think about an external constructor)
                throw new IllegalArgumentException("Found duplicate config name in " + parameter.getDeclaringExecutable().getDeclaringClass().getName());
            }
        }
    }

    public T parse(Node root) {
        if (raw) {
            try {
                return targetConstructor.construct(new Object[]{root});
            } catch (Exception e) {
                throw new IllegalStateException("Could not instantiate " + declaredClass.getName(), e);
            }
        }
        resetEntries();
        if (root.getNodeId() != NodeId.mapping) {
            if (inlineable) {
                return parseInline(root);
            }
            throw new WrongNodeTypeConfigException(root, NodeId.mapping);
        }
        MappingNode rootMap = (MappingNode) root;
        for (NodeTuple tuple : rootMap.getValue()) {
            String name = extractName(tuple.getKeyNode());
            Property entry = nodesByName.get(name);
            if (entry == null) {
                throw new PropertyNotFoundParsingException(tuple.getKeyNode(), name);
            }
            if (entry.parsed != null) {
                NodeTuple duplicate = rootMap.getValue().stream()
                        .filter(n -> n != tuple && extractName(n.getKeyNode()).equals(name))
                        .findAny()
                        .get();
                throw new DuplicatePropertyConfigException(tuple.getKeyNode(), duplicate.getKeyNode(), name);
            }
            Node value = tuple.getValueNode();
            entry.parse(value);
        }
        // Check for required but uninitialized properties
        List<Property> uninitializedProperties = nodesByName.values().stream()
                .filter(n -> n.required && n.parsed == null)
                .collect(Collectors.toList());
        if (!uninitializedProperties.isEmpty()) {
            throw new RequiredPropertyNotFoundConfigException(root, uninitializedProperties.stream().map(p -> p.name).collect(Collectors.toList()));
        }
        return constructObject();
    }

    protected T parseInline(Node root) {
        assert inlineable;// Cannot parseInline a non-inlineable object (or at least, it shouldn't be done)

        if (root.getNodeId() == NodeId.scalar) {
            if (positionalArguments.size() != 1) {
                throw new ConfigException(root, "Object does not take only one argument");
            }
            // Single argument properties can be constructed even without an explicit list
            positionalArguments.get(0).parse(root);
        } else if (root.getNodeId() == NodeId.sequence) {
            SequenceNode node = ((SequenceNode)root);
            int argsLen = node.getValue().size();
            if (argsLen > positionalArguments.size()) {
                throw new ConfigException(root, "Too many arguments (max: " + positionalArguments.size() + ")");
            }
            for (int i = 0; i < argsLen; i++) {
                positionalArguments.get(i).parse(node.getValue().get(i));
            }
        } else {
            throw new WrongNodeTypeConfigException(root, NodeId.scalar, NodeId.sequence);
        }
        return constructObject();
    }

    public T parse(Reader reader) {
        return parse(defaultYaml.compose(reader));
    }

    protected T constructObject() {
        Object[] args = new Object[positionalArguments.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = positionalArguments.get(i).getOrDef();
        }
        try {
            return targetConstructor.construct(args);
        } catch (Exception e) {
            throw new IllegalStateException("Could not instantiate " + declaredClass.getName(), e);
        }
    }

    protected String extractName(Node rawNode) {
        if (rawNode.getNodeId() != NodeId.scalar) {
            throw new WrongNodeTypeConfigException(rawNode, NodeId.scalar);
        }
        return ((ScalarNode) rawNode).getValue();
    }

    protected Class<?> extractClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        } else {
            throw new IllegalStateException("Cannot find class for " + type);
        }
    }

    protected Class<?> boxPrimitiveClass(Class<?> clazz) {
        if (clazz == Byte.TYPE)      return Byte.class;
        if (clazz == Short.TYPE)     return Short.class;
        if (clazz == Integer.TYPE)   return Integer.class;
        if (clazz == Long.TYPE)      return Long.class;
        if (clazz == Float.TYPE)     return Float.class;
        if (clazz == Double.TYPE)    return Double.class;
        if (clazz == Character.TYPE) return Character.class;
        if (clazz == Boolean.TYPE)   return Boolean.class;
        throw new IllegalStateException("Illegal primitive " + clazz);
    }

    protected Parser selectParser(Class<?> clazz, Type type) {
        if (Map.class.isAssignableFrom(clazz)) {
            return mappingParser(clazz, type);
        } else if (clazz.isArray()) {
            return arrayParser(clazz, type);
        } else if (Collection.class.isAssignableFrom(clazz)) {
            return sequenceParser(clazz, type);
        } else if (clazz == PlaceholderValue.class) {
            return placeholderValueParser(clazz, type);
        } else {
            return scalarParser(clazz, type);
        }
    }

    protected Parser mappingParser(Class mapClass, Type rawType) {
        ParameterizedType parType = (ParameterizedType) rawType;
        Type[] types = parType.getActualTypeArguments();
        // A map should have 2 type arguments, Map<K, V>
        Type keyType =  types[0];
        Type valueType =  types[1];

        Class<?> keyClass = extractClass(keyType);
        Class<?> valueClass = extractClass(valueType);

        Parser keyParser = selectParser(keyClass, keyType);
        Parser valueParser = selectParser(valueClass, valueType);

        if (mapClass == SortedMap.class) {
            return new MapParser(TreeMap::new, keyParser, valueParser);
        } else if (mapClass == Map.class)  {
            return new MapParser(HashMap::new, keyParser, valueParser);
        } else if (mapClass == EnumMap.class) {
            return new MapParser(() -> new EnumMap(keyClass), keyParser, valueParser);
        } else {
            throw new UnparsableConfigClass(mapClass);
        }
    }

    protected Parser arrayParser(Class clazz, Type rawType) {
        Type arrayType;
        Class arrayClass;
        if (rawType instanceof GenericArrayType) {// Generics on array found (ex. List<Integer>[])
            GenericArrayType type = (GenericArrayType) rawType;// Null if there are no arguments in array
            arrayType = type.getGenericComponentType();
            arrayClass = extractClass(arrayType);
        } else {// No generics found, nothing to lose
            arrayClass = clazz.getComponentType();
            arrayType = arrayClass;
        }
        Parser parser = new ArrayParser(arrayClass, selectParser(arrayClass, arrayType));
        return new CheckerParser(parser, ImmutableSet.of(Tag.SEQ));
    }

    protected Parser sequenceParser(Class clazz, Type rawType) {
        ParameterizedType type = (ParameterizedType) rawType;

        Type valType = type.getActualTypeArguments()[0];
        Class valClass = extractClass(valType);
        Parser valParser = selectParser(valClass, valType);

        Parser parser;
        final List<Tag> expectedTags;

        if (SortedSet.class == clazz) {
            expectedTags = ImmutableList.of(Tag.SET);
            parser = new CollectionParser(TreeSet::new, valParser);
        } else if (Set.class == clazz) {
            expectedTags = ImmutableList.of(Tag.SET);
            parser = new CollectionParser(HashSet::new, valParser);
        } else if (List.class == clazz) {
            expectedTags = ImmutableList.of(Tag.SEQ);
            parser = new CollectionParser(ArrayList::new, valParser);
        } else if (EnumSet.class == clazz) {
            expectedTags = ImmutableList.of(Tag.SET);
            parser = new CollectionParser(() -> EnumSet.noneOf(valClass), valParser);
        } else {
            throw new UnparsableConfigClass(clazz);
        }

        return new CheckerParser(parser, expectedTags);
    }

    protected Parser scalarParser(Class clazz, Type type) {
        ScalarParser parser;
        List<Tag> expectedTags;

        if (clazz.isPrimitive()) {
            clazz = boxPrimitiveClass(clazz);
        }
        if (Number.class.isAssignableFrom(clazz)) {
            expectedTags = ImmutableList.of(Tag.INT, Tag.FLOAT);
            parser =  numberParser(clazz);
        } else if (clazz == Boolean.class) {
            expectedTags = ImmutableList.of(Tag.BOOL);
            parser = s -> {
                switch (s.getValue().toLowerCase()) {
                    case "yes":
                    case "on":
                    case "true":
                        return true;
                    case "no":
                    case "off":
                    case "false":
                        return false;
                }
                throw new WrongValueConfigException(s, s.getValue(), "boolean");
            };
        } else if (clazz == String.class) {
            expectedTags = ImmutableList.of(Tag.STR);
            parser = ScalarNode::getValue;
        } else if (clazz == Character.class) {
            expectedTags = ImmutableList.of(Tag.STR);
            parser = n -> {
                String s = n.getValue();
                if (s.length() == 0) return null;
                if (s.length() != 1) throw new WrongValueConfigException(n, s, "character");
                return s.charAt(0);
            };
        } else if (clazz == Date.class) {
            expectedTags = ImmutableList.of(Tag.TIMESTAMP);
            parser = TIMESTAMP_CONSTRUCTOR::construct;
        } else if (clazz == Calendar.class) {
            expectedTags = ImmutableList.of(Tag.TIMESTAMP);
            SafeConstructor.ConstructYamlTimestamp constructor = new SafeConstructor.ConstructYamlTimestamp();
            parser = n -> {
                constructor.construct(n);
                return constructor.getCalendar();
            };
        } else if (clazz == UUID.class) {
            expectedTags = ImmutableList.of(Tag.STR);
            parser = n -> UUID.fromString(n.getValue());
        } else {
            ConfigParser rawParser = ConfigParser.tryFromClass(clazz);
            if (rawParser != null) {
                return ConfigParser.tryFromClass(clazz)::parse;
            } else if (clazz.isEnum()) {
                // Enums AFTER custom object (custom override is permitted)
                expectedTags = ImmutableList.of(Tag.STR);
                final Class cls = clazz;
                parser = n -> {
                    String s = n.getValue().replace(' ', '_').toUpperCase();
                    try {
                        return  Enum.valueOf(cls, s);
                    } catch (IllegalArgumentException e) {
                        throw new WrongValueConfigException(n, n.getValue(), cls.getName());
                    }
                };
            } else {
                throw new UnparsableConfigClass(clazz);
            }
        }
        return new CheckerParser(parser.normalize(), expectedTags);
    }

    protected ScalarParser numberParser(Class<? extends Number> dstClass) {
        Function<String, Number> parser;
        if      (dstClass == Byte.class)    parser = Byte::parseByte;
        else if (dstClass == Short.class)   parser = Short::parseShort;
        else if (dstClass == Integer.class) parser = Integer::parseInt;
        else if (dstClass == Long.class)    parser = Long::parseLong;
        else if (dstClass == Float.class)   parser = Float::parseFloat;
        else if (dstClass == Double.class)  parser = Double::parseDouble;
        else if (dstClass == BigDecimal.class) parser = BigDecimal::new;
        else throw new IllegalStateException("Unexpected primitive " + dstClass);

        return node -> {
            try {
                return parser.apply(node.getValue());
            } catch (NumberFormatException e) {
                throw new CannotConvertNumberConfigException(node, node.getValue(), dstClass.getSimpleName());
            }
        };
    }

    //TODO: Support complex types (PlaceholderValue<List<Integer>> and map-seq types won't work)
    protected Parser placeholderValueParser(Class<?> placeholderValueClass, Type rawType) {
        ParameterizedType type = (ParameterizedType) rawType;

        Type valType = type.getActualTypeArguments()[0];
        Class valClass = extractClass(valType);
        Parser valParser = selectParser(valClass, valType);

        return rawNode -> {
            checkNodeId(rawNode, NodeId.scalar);
            ScalarNode node = translateColors((ScalarNode) rawNode);
            //TODO Test Translate colors and such
            if (!PlaceholderUtil.hasPlaceholders(node.getValue())) {
                return PlaceholderValue.fake(valParser.parse(rawNode));
            } else {
                // Replace the node's value with the resolved placeholders value
                return (PlaceholderValue) (player, local) ->
                        valParser.parse(replaceValue(node, PlaceholderUtil.resolve(player, node.getValue(), local)));
            }
        };
    }

    private ScalarNode translateColors(ScalarNode original) {
        String translatedValue = TextUtil.translatePlain(original.getValue());
        if (translatedValue.equals(original.getValue())) return original;
        return replaceValue(original, translatedValue);
    }

    private ScalarNode replaceValue(ScalarNode original, String newValue) {
        return new ScalarNode(original.getTag(), original.isResolved(), newValue, original.getStartMark(),
                original.getEndMark(), original.getStyle());
    }

    protected void resetEntries() {
        nodesByName.values().forEach((Property n) -> n.parsed = null);
    }

    public static <T> ConfigParser<T> getIfPresent(Class<T> clazz) {
        return parsersByClass.get(clazz);
    }

    private static <T> ConfigParser<T> createForClass(Class<T> clazz) {
        Constructor<T>[] constructors = (Constructor<T>[]) clazz.getDeclaredConstructors();
        Constructor<T> targetConstructor = null;
        for (Constructor<T> constructor : constructors) {
            if (constructor.isAnnotationPresent(ConfigConstructor.class)) {
                if (targetConstructor != null) {
                    throw new IllegalStateException("Multiple ConfigConstructors in class " + clazz.getName());
                }
                targetConstructor = constructor;
            }
        }
        if (targetConstructor == null) {
            return null;
        }
        targetConstructor.setAccessible(true);
        Parameter[] parameters = targetConstructor.getParameters();
        ObjectConstructor<T> refinedConstructor = targetConstructor::newInstance;
        boolean inlineable = targetConstructor.getAnnotation(ConfigConstructor.class).inlineable();
        return new ConfigParser<>(clazz, parameters, refinedConstructor, inlineable);
    }

    public static <T> ConfigParser<T> tryFromClass(Class<T> clazz) {
        // Check if the parser is present, if it's not registered
        // checks if it is parsable.
        // If parsable then register its ConfigParser, elsewise return null
        ConfigParser<T> foundOrComputed = parsersByClass.get(clazz);
        if (foundOrComputed == null) {
            foundOrComputed = ConfigParser.createForClass(clazz);
            if (foundOrComputed == null) {
                // Computation failed (no ConfigConstructor found)
                return null;
            }
            parsersByClass.put(clazz, foundOrComputed);
        }
        return foundOrComputed;
    }

    public static <T> ConfigParser<T> fromClass(Class<T> clazz) {
        ConfigParser<T> foundOrComputed = tryFromClass(clazz);
        if (foundOrComputed == null) {
            // Computation failed (no ConfigConstructor found)
            throw new IllegalArgumentException("Cannot find ConfigConstructor in class " + clazz.getName());
        }
        return foundOrComputed;
    }

    public static void createFrom(ConfigExternalDeclarator declarator) {
        int matchedMethods = 0;

        for (Method method : declarator.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(ConfigConstructor.class)) continue;
            ConfigConstructor annotation = method.getAnnotation(ConfigConstructor.class);
            method.setAccessible(true);
            Parameter[] parameters = method.getParameters();
            Class<?> returnType = method.getReturnType();
            ObjectConstructor refinedConstructor = args -> method.invoke(declarator, args);
            ConfigParser parser = new ConfigParser(returnType, parameters, refinedConstructor, annotation.inlineable());
            if (parsersByClass.putIfAbsent(returnType, parser) != null) {
                throw new IllegalStateException("Parser for class " + returnType.getName() + " already registered!");
            }
            matchedMethods++;
        }
        if (matchedMethods == 0) {
            throw new IllegalStateException("Class " + declarator.getClass() + " does not define any ConfigConstructor!");
        }
    }

    public static void checkNodeTag(Node node, Collection<Tag> expectedTags) {
        if (!expectedTags.contains(node.getTag())) {
            throw new WrongNodeTypeConfigException(node, expectedTags.toArray(new Tag[0]));
        }
    }

    public static void checkNodeTag(Node node, Tag expectedTag) {
        if (expectedTag != node.getTag()) {
            throw new WrongNodeTypeConfigException(node, expectedTag);
        }
    }

    public static void checkNodeId(Node node, Collection<NodeId> expectedIds) {
        if (!expectedIds.contains(node.getNodeId())) {
            throw new WrongNodeTypeConfigException(node, expectedIds.toArray(new NodeId[0]));
        }
    }

    public static void checkNodeId(Node node, NodeId expectedIds) {
        if (expectedIds != node.getNodeId()) {
            throw new WrongNodeTypeConfigException(node, expectedIds);
        }
    }

    protected class Property {
        public String name;
        public boolean required;
        public Parser parser;
        public Object def = null;
        public Object parsed;

        public Property(Parameter parameter) {
            name = "";
            required = true;
            ConfigProperty annotation = parameter.getAnnotation(ConfigProperty.class);
            if (annotation != null) {
                name = annotation.name();
                required = !annotation.optional();
            }
            if (name.isEmpty()) {
                if (!parameter.isNamePresent() && !inlineable) {
                    throw new IllegalArgumentException("Cannot find name of " + parameter.getName()
                            + " in class " + parameter.getDeclaringExecutable().getDeclaringClass().getName()
                            + " Use @ConfigProperty or compile with -parameters");
                }
                name = parameter.getName();
            }
            if (!required && parameter.getType().isPrimitive()) {
                throw new IllegalArgumentException("Cannot have optional primitive (parameter: " +
                        parameter.getName() +
                        ", class: " +
                        parameter.getDeclaringExecutable().getDeclaringClass() + ")");
            }
            if (parameter.getType() == Optional.class) {
                required = false;
                def = Optional.empty();
                Type optType = ((ParameterizedType)parameter.getParameterizedType()).getActualTypeArguments()[0];
                Parser nonOptionalParser = selectParser(extractClass(optType), optType);
                parser = node -> Optional.of(nonOptionalParser.parse(node));
            } else {
                parser = selectParser(parameter.getType(), parameter.getParameterizedType());
            }
        }

        public void parse(Node node) {
            parsed = parser.parse(node);
        }

        public Object getOrDef() {
            return parsed == null ? def : parsed;
        }
    }

    public interface Parser {
        Object parse(Node node);
    }

    protected interface ScalarParser {
        Object parse(ScalarNode node);

        default Parser normalize() {
            return node -> parse((ScalarNode) node);
        }
    }

    public interface ObjectConstructor<T> {
        T construct(Object[] arguments) throws Exception;
    }

    public static class CheckerParser implements Parser {
        private final Parser realParser;
        private final Collection<Tag> expectedTags;

        public CheckerParser(Parser realParser, Collection<Tag> expectedTags) {
            this.realParser = realParser;
            this.expectedTags = expectedTags;
        }

        @Override
        public Object parse(Node node) {
            if (!expectedTags.contains(node.getTag())) {
                throw new WrongNodeTypeConfigException(node, expectedTags.toArray(new Tag[0]));
            }
            return realParser.parse(node);
        }
    }

    public static class CollectionParser implements Parser {
        private final Supplier<Collection> collectionSupplier;
        private final Parser parser;

        public CollectionParser(Supplier<Collection> setSupplier, Parser parser) {
            this.collectionSupplier = setSupplier;
            this.parser = parser;
        }

        @Override
        public Object parse(Node rawNode) {
            SequenceNode node = (SequenceNode) rawNode;
            Collection collection = collectionSupplier.get();
            for (Node entry : node.getValue()) {
                collection.add(parser.parse(entry));
            }

            return collection;
        }
    }

    public static class ArrayParser implements Parser {
        private final Class<?> arrayType;
        private final Parser parser;
        private final ArraySetter arraySetter;

        public ArrayParser(Class<?> arrayType, Parser parser) {
            this.arrayType = arrayType;
            this.parser = parser;
            this.arraySetter = selectSetter();
        }

        @Override
        public Object parse(Node rawNode) {
            SequenceNode node = (SequenceNode) rawNode;
            int length = node.getValue().size();
            Object array = Array.newInstance(arrayType, length);
            List<Node> entries = node.getValue();
            for (int i = 0; i < length; i++) {
                arraySetter.set(array, i, parser.parse(entries.get(i)));
            }

            return array;
        }

        private ArraySetter selectSetter() {
            if (arrayType == Byte.TYPE) {
                return (arr, i, o) -> Array.setByte(arr, i, (Byte) o);
            } else if (arrayType == Short.TYPE) {
                return (arr, i, o) -> Array.setShort(arr, i, (Short) o);
            } else if (arrayType == Integer.TYPE) {
                return (arr, i, o) -> Array.setInt(arr, i, (Integer) o);
            } else if (arrayType == Long.TYPE) {
                return (arr, i, o) -> Array.setLong(arr, i, (Long) o);
            }  else if (arrayType == Float.TYPE) {
                return (arr, i, o) -> Array.setFloat(arr, i, (Long) o);
            } else if (arrayType == Double.TYPE) {
                return (arr, i, o) -> Array.setDouble(arr, i, (Double) o);
            } else if (arrayType == Character.TYPE) {
                return (arr, i, o) -> Array.setChar(arr, i, (Character) o);
            } else if (arrayType == Boolean.TYPE) {
                return (arr, i, o) -> Array.setBoolean(arr, i, (Boolean) o);
            } else {
                return Array::set;
            }
        }

        interface ArraySetter {
            void set(Object array, int index, Object val);
        }
    }

    public static class MapParser implements Parser {
        private final Supplier<Map> mapSupplier;
        private final Parser keyParser, valueParser;

        public MapParser(Supplier<Map> mapSupplier, Parser keyParser, Parser valueParser) {
            this.mapSupplier = mapSupplier;
            this.valueParser = valueParser;
            this.keyParser = keyParser;
        }

        @Override
        public Object parse(Node rawNode) {
            MappingNode node = (MappingNode) rawNode;
            Map map = mapSupplier.get();
            for (NodeTuple entry : node.getValue()) {
                map.put(keyParser.parse(entry.getKeyNode()), valueParser.parse(entry.getValueNode()));
            }

            return map;
        }
    }
}

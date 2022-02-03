package test;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;

public class Test {
    private static final Map<String, IndexType> INDEX_TYPE_MAP = Arrays.stream(IndexType.values())
            .collect(collectingAndThen(toMap(Enum::toString, Function.identity()), ImmutableMap::copyOf));

    private static final Map<String, IndexType> INDEX_TYPE_MAP2 = ImmutableMap.copyOf(
            Arrays.stream(IndexType.values())
                    .collect(toMap(Enum::toString, Function.identity())));

}

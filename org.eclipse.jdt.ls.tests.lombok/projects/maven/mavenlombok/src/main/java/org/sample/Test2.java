package org.sample;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
@Builder
@Getter
public class Test2 {
	@Singular("singular")
	List<String> singulars;
	List<String> normals;
}

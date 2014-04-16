package au.edu.alveo.uima.tutorial;

import com.nicta.uimavlab.conversions.UIMAAlveoTypeNameMapping;
import com.nicta.uimavlab.conversions.UIMAToAlveoAnnConverter;
import com.nicta.vlabclient.TextRestAnnotation;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.TreeSet;

/** Class to handle conversion of POS annotations more sensibly. By default, there are broad
 * leaf annotations for different POS types (V, N etc). If we want all POS annotations to have
 * the same type in Alveo, we need to create a custom map
 *
 * (alternatively we could also write our own UIMA CasAnnotator to output UIMA types which
 * are more straightforwardly convertible. We merely need to have the desired type
 * encoded in a feature (or be happy with the URL automatically generated from its
 * actual type name in {@link com.nicta.uimavlab.conversions.DefaultUIMAToAlveoAnnConverter},
 * and the desired label encoded in another feature)
 */
public class DKProPosConverter implements UIMAToAlveoAnnConverter {
	private static final Logger LOG = LoggerFactory.getLogger(DKProPosConverter.class);

	private TypeSystem ts = null;
	private Feature labelFeature;
	private Type primaryType;
	private Set<Type> allowedTypes = new TreeSet<Type>();

	private static final String LABEL_FEATURE_NAME = "de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS:PosValue";
	private static final String PRIMARY_TYPE_NAME = "de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS";
	private static final String PRIMARY_TYPE_ALVEO_URI = UIMAAlveoTypeNameMapping.getUriForTypeName(PRIMARY_TYPE_NAME);

	@Override
	public void setTypeSystem(TypeSystem ts) {
		if (ts.equals(this.ts))
			return;
		this.ts = ts;
		labelFeature = this.ts.getFeatureByFullName(LABEL_FEATURE_NAME);
		if (labelFeature == null)
			LOG.error("Type system had no feature {}", LABEL_FEATURE_NAME);
		primaryType = this.ts.getType(PRIMARY_TYPE_NAME);
		if (primaryType == null)
			LOG.error("Type system had no type named {}", PRIMARY_TYPE_NAME);
		allowedTypes.clear();
		if (primaryType != null)
			allowedTypes.addAll(ts.getProperlySubsumedTypes(primaryType));
	}

	@Override
	public TextRestAnnotation convertToAlveo(AnnotationFS ann) throws InvalidAnnotationTypeException, NotInitializedException {
		if (!handlesTypeName(ann.getType().getName()))
			throw new InvalidAnnotationTypeException("Annotation " + ann + " is not a subtype of " + PRIMARY_TYPE_NAME);
		return new TextRestAnnotation(PRIMARY_TYPE_ALVEO_URI, ann.getFeatureValueAsString(labelFeature), ann.getBegin(), ann.getEnd());
	}

	@Override
	public String getAlveoTypeUriForTypeName(String uimaTypeName) {
		return PRIMARY_TYPE_ALVEO_URI;
	}

	@Override
	public boolean handlesTypeName(String uimaTypeName) {
		Type uimaType = ts.getType(uimaTypeName);
		if (uimaType == null)
			return false;
		return allowedTypes.contains(uimaType);
	}
}

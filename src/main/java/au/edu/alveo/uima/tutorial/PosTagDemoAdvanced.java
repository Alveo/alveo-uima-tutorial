package au.edu.alveo.uima.tutorial;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import au.edu.alveo.uima.ItemAnnotationUploader;
import au.edu.alveo.uima.ItemListCollectionReader;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpPosTagger;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpSegmenter;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A more advanced version of PosTagDemo, which shows one way in which we can remap UIMA
 * annotations to Alveo annotations if the default conversion doesn't do what we want.
 *
 * To convert from UIMA to Alveo, we need a) a value for the type URI and b) a value for the label.
 *
 * The default conversion attempts to infer reasonable values for these with minimal configuration.
 * The label defaults to the empty string. However, the parameter
 * ItemAnnotationUploader.PARAM_LABEL_FEATURE_NAMES stores an array of strings which are treated
 * as UIMA feature names. If any of these names are encountered on a UIMA annotation, its string
 * value is used as the label of the corresponding UIMA annotation.
 *
 * Similary, the Alveo type URI defaults to a URI constructed from the UIMA type name (by prepending
 * "http://", reversing all components of the UIMA type name but the last, then appending the final
 * component -- so 'org.example.foo.Bar' would be converted to 'http://foo.example.org/Bar'. However
 * if any features from ItemAnnotationUploader.PARAM_ANNTYPE_FEATURE_NAMES are found on an annotation,
 * those features are used instead.
 *
 * However, these defaults may not always be what we want. For example, we may decide after looking
 * at the conversion produced by PosTagDemo that the type URIs for the POS annotations obscure
 * the fact that they are all parts-of-speech, since verb POS annotations get a type like
 * "http://pos.type.lexmorph.api.core.dkpro.ukp.tudarmstadt.de/VV" while noun annotations get a
 * type like "http://pos.type.lexmorph.api.core.dkpro.ukp.tudarmstadt.de/VV". Perhaps it would
 * be more desirable for both to have type
 * "http://pos.type.lexmorph.api.core.dkpro.ukp.tudarmstadt.de/POS"
 *
 * One way to achieve this this would be to create a new CAS annotator which reads in some source
 * annotations and converts them to new annotations where the alveo-uima default conversions work
 * sensibly. This may be unwieldy for some tasks however -- it is necessary to add new types to the
 * type system just to help with data format conversion.
 *
 * The slightly simpler approach involves implementing au.edu.alveo.uima.conversions.UIMAToAlveoAnnConverter
 * (see the documentation on the interface for more details) to customise the remapping process,
 * then supplying the name of the implementing class to the processing components.
 *
 */
public class PosTagDemoAdvanced {

	private static void runPipeline(String serverUrl, String apiKey, String xmiDir, String itemListId)
			throws UIMAException, IOException {

		/* Here is the list of extra converters we want to use to do the conversion.
		 * Note that this needs to be supplied to collection reader as well to
		 * get sensible handling of type URIs. See the source of DKProPosConverter (which
		 * implements UIMAToAlveoAnnConverter, as is required)
		 * to find out how this very simple converter works.
		 */
		String[] extraConverters = new String[] {
				"au.edu.alveo.uima.tutorial.DKProPosConverter"
		};

		/* As in PosTagDemo, we have to use this special factory method */
		CollectionReaderDescription reader = ItemListCollectionReader.createDescription(
				ItemListCollectionReader.PARAM_ALVEO_BASE_URL, serverUrl,
				ItemListCollectionReader.PARAM_ALVEO_API_KEY, apiKey,
				ItemListCollectionReader.PARAM_ALVEO_ITEM_LIST_ID, itemListId,
				ItemListCollectionReader.PARAM_INCLUDE_RAW_DOCS, false,
				ItemListCollectionReader.PARAM_ANNOTATION_CONVERTERS, extraConverters);

		/* Instantiate some standard DKPro components */
		AnalysisEngineDescription segmenter = AnalysisEngineFactory.createEngineDescription(OpenNlpSegmenter.class);
		AnalysisEngineDescription posTagger = AnalysisEngineFactory.createEngineDescription(OpenNlpPosTagger.class);

		/** The same meaning as in PosTagDemo */
		String[] uploadableTypes = new String[] {
				"de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS",
				"de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence"
		};

		/** The pipeline instantiation is the same as in PosTagDemo */
		AnalysisEngineDescription uploader = AnalysisEngineFactory.createEngineDescription(ItemAnnotationUploader.class,
				ItemAnnotationUploader.PARAM_ALVEO_BASE_URL, serverUrl,
				ItemAnnotationUploader.PARAM_ALVEO_API_KEY, apiKey,
				ItemAnnotationUploader.PARAM_ANNOTATION_CONVERTERS, extraConverters,
				ItemAnnotationUploader.PARAM_UPLOADABLE_UIMA_TYPE_NAMES, uploadableTypes);
		AnalysisEngineDescription aggAe = AnalysisEngineFactory.createEngineDescription(segmenter, posTagger, uploader);
		SimplePipeline.runPipeline(reader, aggAe);
	}


	/* The rest is just cruft for the command-line */

	protected static class CLParams {

		@Parameter(names = {"-u", "--server-url"}, required = true, description = "Base URL of HCS vLab server")
		private String serverUrl;

		@Parameter(names = {"-k", "--api-key"}, required = true,
				description = "API key for for your user account, obtainable from the web interface")
		private String apiKey;

		@Parameter(names = { "--help", "-h", "-?" }, help = true, description = "Display this help text")
		private boolean help;

		@Parameter(names = { "--descriptor-dir"}, required = false,
				description = "If provided, a directory where descriptors will be written")
		private String descriptorDir = null;

		@Parameter(names = { "-o", "--xmi-output-dir"}, required = true,
				description = "The directory where the XMI files produced will be written")
		private String xmiDir;

		@Parameter(names = { "-i", "--item-list-id"}, required = true, description =
				"The item list ID to convert to XMI")
		private String itemListId;

	}

	public static void main(String[] args) throws Exception {
		CLParams params = new CLParams();
		JCommander jcom = new JCommander(params, args);
		jcom.setProgramName(PosTagDemo.class.getName());
		if (params.help) {
			jcom.usage();
			return;
		}
		runPipeline(params.serverUrl, params.apiKey, params.xmiDir, params.itemListId);
	}

}

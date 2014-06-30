package au.edu.alveo.uima.tutorial;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import au.edu.alveo.uima.ItemAnnotationUploader;
import au.edu.alveo.uima.ItemListCollectionReader;
import au.edu.alveo.uima.examples.XmiWriterCasConsumer;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpPosTagger;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpSegmenter;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;

import java.io.IOException;

/**
 * Created by amack on 7/04/14.
 */
public class PosTagDemo {
	/**
	 * Run a pipeline which adds POS tags and sentence boundaries to the items.
	 *
	 * The pipeline reads an existing item list from the Alveo server (including all of its server-side
	 * annotations) and uploads any annotations which are stored in the CAS after running
	 * the pipeline (if their types match a supplied list).
	 *
	 * @param serverUrl  The base URL of the Alveo server
	 * @param apiKey     The API key for the Alveo server
	 * @param xmiDir     The directory to write the processed files to (for debugging)
	 * @param itemListId The ID of the item list to read from the server
	 * @throws UIMAException
	 * @throws IOException
	 */
	private static void runPipeline(String serverUrl, String apiKey, String xmiDir, String itemListId)
			throws UIMAException, IOException {

		/* First we need to create a collection reader. In general this will be an ItemListCollectionReader
		 * since we want to make sure we have the appropriate metadata, and we are only adding annotations
		 * to existing items.
		 * To do this, we need to create a collection reader description. Ordinarily in uimaFIT we would create
		 * one of these using CollectionReaderFactory.createReaderDescription() but due to the fact
		 * that we need to dynamically generate a type system here, we must instead use the following static
		 * factory method so that we can infer the type system by querying the supplied Alveo server.
		 */
		CollectionReaderDescription reader = ItemListCollectionReader.createDescription(
				ItemListCollectionReader.PARAM_ALVEO_BASE_URL, serverUrl,
				ItemListCollectionReader.PARAM_ALVEO_API_KEY, apiKey,
				ItemListCollectionReader.PARAM_ALVEO_ITEM_LIST_ID, itemListId,
				ItemListCollectionReader.PARAM_INCLUDE_RAW_DOCS, false);

		// These are just standard components from DKPro-core
		AnalysisEngineDescription posTagger = AnalysisEngineFactory.createEngineDescription(OpenNlpPosTagger.class);
		AnalysisEngineDescription segmenter = AnalysisEngineFactory.createEngineDescription(OpenNlpSegmenter.class);
		/* Note that a better solution than using the default sentence segmenter would be to to
		 * use the speaker turn annotations (in the case of eg GCSAusE) and translate these into
		 * sentence segmentation annotations (using a custom-made CAS annotator probably)
		 * which would be understood by DKPro's POS tagger */

		/* These are the annotation types which we care about. Any UIMA annotation which doesn't
		 * have one of these type names will be ignored. In addition any annotation which already
		 * has a corresponding annotation on the server with the same type, label and span will
		 * be ignored.
		 */
		String[] uploadableTypes = new String[]{
				"de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS",
				"de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence"
		};

		/* These are the names of features which are used to populate the "label" attribute of
		 * the Alveo annotation. When uploading annotations, we first check if an annotation
		 * which is remaining after the UIMA pipeline has run (NB: the pipeline will likely
		 * include the annotations which have just been downlo
		 */
		String[] labelFeatures = new String[]{
				"de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS:PosValue",
				ItemAnnotationUploader.DEFAULT_LABEL_FEATURE
		};

		/* Now we need to create an Analysis Engine to do the actual work of uploading the annotations.
		 * This is what the ItemAnnotationUploader class is designed for. Unlike with the collection
		 * reader, we don't need to call a custom method to create the description, as we don't
		 * need to dynamically infer a new type system here (and the overall type system for the
		 * pipeline will merge in the type systems of the collection reader anyway). So we just
		 * use the standard uimaFIT method in this case, setting the appropriate parameters.
		 */
		AnalysisEngineDescription uploader = AnalysisEngineFactory.createEngineDescription(ItemAnnotationUploader.class,
				ItemAnnotationUploader.PARAM_ALVEO_BASE_URL, serverUrl,
				ItemAnnotationUploader.PARAM_ALVEO_API_KEY, apiKey,
				ItemAnnotationUploader.PARAM_LABEL_FEATURE_NAMES, labelFeatures,
				ItemAnnotationUploader.PARAM_UPLOADABLE_UIMA_TYPE_NAMES, uploadableTypes);

		/** Now let's create an Analysis engine which combines the various components we've instantiated */
		AnalysisEngineDescription aggAe;
		if (xmiDir != null) {
			/* if this is provided, we'll also write out CASes in serialized XML format to disk
			 * after running the other processing components, but before uploading the annotations.
			 * This is useful for debugging
			 */
			AnalysisEngineDescription casWriter = AnalysisEngineFactory.createEngineDescription(
					XmiWriterCasConsumer.class, XmiWriterCasConsumer.PARAM_OUTPUTDIR, xmiDir);
			casWriter.getAnalysisEngineMetaData().setTypeSystem(reader.getCollectionReaderMetaData().getTypeSystem());
			aggAe = AnalysisEngineFactory.createEngineDescription(segmenter, posTagger, casWriter, uploader);
		} else {
			aggAe = AnalysisEngineFactory.createEngineDescription(segmenter, posTagger, uploader);
		}

		// Now we simply run the pipeline. uimaFIT makes this very easy.
		SimplePipeline.runPipeline(reader, aggAe);
	}


	/** The rest of the class is just for running from the command line and parsing the arguments */

	protected static class CLParams {

		@Parameter(names = {"-u", "--server-url"}, required = true, description = "Base URL of HCS vLab server")
		private String serverUrl;

		@Parameter(names = {"-k", "--api-key"}, required = true,
				description = "API key for for your user account, obtainable from the web interface")
		private String apiKey;

		@Parameter(names = {"--help", "-h", "-?"}, help = true, description = "Display this help text")
		private boolean help;

		@Parameter(names = {"--descriptor-dir"}, required = false,
				description = "If provided, a directory where descriptors will be written")
		private String descriptorDir = null;

		@Parameter(names = {"-o", "--xmi-output-dir"}, required = false,
				description = "The directory where the XMI files produced will be written")
		private String xmiDir = null;

		@Parameter(names = {"-i", "--item-list-id"}, required = true, description =
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

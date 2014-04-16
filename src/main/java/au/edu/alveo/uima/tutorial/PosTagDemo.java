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
import org.apache.uima.fit.pipeline.SimplePipeline;

import java.io.IOException;

/**
 * Created by amack on 7/04/14.
 */
public class PosTagDemo {
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

		@Parameter(names = {"-o", "--xmi-output-dir"}, required = true,
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

	/**
	 * Run a pipeline which adds POS tags and sentence boundaries to the items.
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
		CollectionReaderDescription reader = ItemListCollectionReader.createDescription(
				ItemListCollectionReader.PARAM_ALVEO_BASE_URL, serverUrl,
				ItemListCollectionReader.PARAM_ALVEO_API_KEY, apiKey,
				ItemListCollectionReader.PARAM_ALVEO_ITEM_LIST_ID, itemListId,
				ItemListCollectionReader.PARAM_INCLUDE_RAW_DOCS, false);
		AnalysisEngineDescription segmenter = AnalysisEngineFactory.createEngineDescription(OpenNlpSegmenter.class);
		AnalysisEngineDescription posTagger = AnalysisEngineFactory.createEngineDescription(OpenNlpPosTagger.class);

		String[] labelFeatures = new String[]{
				"de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS:PosValue",
				ItemAnnotationUploader.DEFAULT_LABEL_FEATURE
		};
		String[] uploadableTypes = new String[]{
				"de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS",
				"de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence"
		};
		AnalysisEngineDescription uploader = AnalysisEngineFactory.createEngineDescription(ItemAnnotationUploader.class,
				ItemAnnotationUploader.PARAM_ALVEO_BASE_URL, serverUrl,
				ItemAnnotationUploader.PARAM_ALVEO_API_KEY, apiKey,
				ItemAnnotationUploader.PARAM_LABEL_FEATURE_NAMES, labelFeatures,
				ItemAnnotationUploader.PARAM_UPLOADABLE_UIMA_TYPE_NAMES, uploadableTypes);
		AnalysisEngineDescription aggAe;
		if (xmiDir != null) {
			AnalysisEngineDescription casWriter = AnalysisEngineFactory.createEngineDescription(
					XmiWriterCasConsumer.class, XmiWriterCasConsumer.PARAM_OUTPUTDIR, xmiDir);
			casWriter.getAnalysisEngineMetaData().setTypeSystem(reader.getCollectionReaderMetaData().getTypeSystem());
			aggAe = AnalysisEngineFactory.createEngineDescription(segmenter, posTagger, casWriter, uploader);
		} else {
			aggAe = AnalysisEngineFactory.createEngineDescription(segmenter, posTagger, uploader);
		}
		SimplePipeline.runPipeline(reader, aggAe);
	}

}

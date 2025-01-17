package org.clulab.processors.bionlp

import edu.stanford.nlp.ling.CoreAnnotations.{SentencesAnnotation, TokensAnnotation}
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import org.clulab.processors.Document
import org.clulab.processors.bionlp.ner.{HybridNER, KBLoader}
import org.clulab.processors.fastnlp.FastNLPProcessor
import org.clulab.processors.shallownlp.ShallowNLPProcessor

import scala.collection.JavaConversions._

/**
  * A processor for biomedical texts, based on FastNLP with the NN parser, but with different tokenization and NER
  * User: mihais
  * Date: 2/9/17
  */
class FastBioNLPProcessor (internStrings:Boolean = false,
                           withChunks:Boolean = true,
                           withCRFNER:Boolean = true,
                           withRuleNER:Boolean = true,
                           withContext:Boolean = true,
                           withDiscourse:Int = ShallowNLPProcessor.NO_DISCOURSE,
                           maxSentenceLength:Int = 100,
                           removeFigTabReferences:Boolean = true,
                           removeBibReferences:Boolean = true
)
  extends FastNLPProcessor(internStrings, withChunks, useMalt = false, withDiscourse) {

  //lazy val banner = new BannerWrapper
  private lazy val specialTokens = KBLoader.loadSpecialTokens
  private lazy val postProcessor = new BioNLPTokenizerPostProcessor(specialTokens)
  private lazy val preProcessor = new BioNLPPreProcessor(removeFigTabReferences, removeBibReferences)
  private lazy val hybridNER = new HybridNER(withCRFNER, withRuleNER)
  private lazy val posPostProcessor = new BioNLPPOSTaggerPostProcessor

  override def mkTokenizerWithoutSentenceSplitting: StanfordCoreNLP = BioNLPUtils.mkTokenizerWithoutSentenceSplitting

  override def mkTokenizerWithSentenceSplitting: StanfordCoreNLP = BioNLPUtils.mkTokenizerWithSentenceSplitting

  override def postprocessTokens(originalTokens:Array[CoreLabel]):Array[CoreLabel] = postProcessor.process(originalTokens)

  override def preprocessText(origText:String):String = preProcessor.preprocess(origText)

  override def resolveCoreference(doc:Document): Unit = {
    doc.coreferenceChains = None
  }

  /**
    * Improve POS tagging in the Bio domain
    * @param annotation The CoreNLP annotation
    */
  override def postprocessTags(annotation:Annotation) {
    val sas = annotation.get(classOf[SentencesAnnotation])

    sas.foreach{ sa =>
      val tas = sa.get(classOf[TokensAnnotation]).toList.toArray
      posPostProcessor.postprocessCoreLabelTags(tas)
    }
  }

  override def recognizeNamedEntities(doc:Document) {
    hybridNER.recognizeNamedEntities(doc, namedEntitySanityCheck(doc))
  }
}

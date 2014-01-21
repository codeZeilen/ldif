package ldif.util

/*
 * LDIF
 *
 * Copyright 2011-2014 Universität Mannheim, MediaEvent Services GmbH & Co. KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import xml.parsing.NoBindingFactoryAdapter
import ldif.util.ValidationException.ValidationError
import javax.xml.validation.SchemaFactory
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.parsers.SAXParserFactory
import xml.{TopScope, Elem}
import org.xml.sax.{Attributes, SAXParseException, ErrorHandler, InputSource}


/**
 * Reads an XML stream while validating it using a xsd schema file.
 */
class XMLReader extends NoBindingFactoryAdapter {
  private var currentErrors = List[String]()
  private var validationErrors = List[ValidationError]()

  def read(inputSource: InputSource, schemaPath: String): Elem = {
    //Load XML Schema
    val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    val schemaStream = getClass.getClassLoader.getResourceAsStream(schemaPath)
    if (schemaStream == null) throw new ValidationException("XML Schema for configuration file not found")
    val schema = schemaFactory.newSchema(new StreamSource(schemaStream))

    //Create parser
    val parserFactory = SAXParserFactory.newInstance()
    parserFactory.setNamespaceAware(true)
    parserFactory.setFeature("http://xml.org/sax/features/namespace-prefixes", true)
    val parser = parserFactory.newSAXParser()

    //Set Error handler
    val xr = parser.getXMLReader
    val vh = schema.newValidatorHandler()
    vh.setErrorHandler(new ErrorHandler {
      def warning(ex: SAXParseException) {}

      def error(ex: SAXParseException) {
        addError(ex)
      }

      def fatalError(ex: SAXParseException) {
        addError(ex)
      }
    })
    vh.setContentHandler(this)
    xr.setContentHandler(vh)

    //Parse XML
    scopeStack.push(TopScope)
    xr.parse(inputSource)
    scopeStack.pop

    //Add errors without an id
    for(error <- currentErrors) {
      validationErrors ::= ValidationError(error)
    }

    //Return result
    if (validationErrors.isEmpty) {
      rootElem.asInstanceOf[Elem]
    }
    else {
      throw new ValidationException(validationErrors.reverse)
    }
  }

  override def startElement(uri: String, _localName: String, qname: String, attributes: Attributes) {
    for(idAttribute <- Option(attributes.getValue("id"))) {
      val id = Identifier(idAttribute)

      for(error <- currentErrors) {
        validationErrors ::= ValidationError(error, Some(id), Some(_localName))
      }

      currentErrors = Nil
    }

    super.startElement(uri, _localName, qname, attributes)
  }

  /**
   * Formats a XSD validation exception.
   */
  protected def addError(ex: SAXParseException) = {
    //The error message without prefixes like "cvc-complex-type.2.4.b:"
    val error = ex.getMessage.split(':').tail.mkString.trim

    currentErrors ::= error
  }
}

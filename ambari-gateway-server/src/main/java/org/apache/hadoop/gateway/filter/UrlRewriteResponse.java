/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.filter;

import apple.laf.JRSUIConstants;
import org.apache.hadoop.gateway.util.urltemplate.Resolver;
import org.apache.hadoop.gateway.util.urltemplate.Template;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Resolver;
import org.apache.hadoop.gateway.util.urltemplate.Rewriter;
import org.apache.hadoop.gateway.util.urltemplate.Template;

import javax.servlet.FilterConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 *
 */
public class UrlRewriteResponse extends HttpServletResponseWrapper implements Resolver {

  private static final Set<String> IGNORE_HEADER_NAMES = new HashSet<String>();
  static {
    IGNORE_HEADER_NAMES.add( "Content-Length" );
  }

  private static final Set<String> REWRITE_HEADER_NAMES = new HashSet<String>();
  static {
    REWRITE_HEADER_NAMES.add( "Location" );
  }

  private FilterConfig config;
  private Rewriter rewriter;
  private HtmlUrlRewritingOutputStream output;

  public UrlRewriteResponse( FilterConfig config, Rewriter rewriter, HttpServletResponse response ) throws IOException {
    super( response );
    this.config = config;
    this.rewriter = rewriter;
    this.output = null;
  }

  protected boolean rewriteHeader( String name ) {
    return REWRITE_HEADER_NAMES.contains( name );
  }

  protected boolean ignoreHeader( String name ) {
    return IGNORE_HEADER_NAMES.contains( name );
  }

  private String rewriteHeaderValue( String value ) {
    try {
      Template input = Parser.parse( value );
      String output = value;
      URI uri = rewriter.rewrite( input, this );
      if( uri != null ) {
        output = uri.toString();
      }
      return output;
    } catch( URISyntaxException e ) {
      throw new IllegalArgumentException( e );
    }
  }

  // Ignore the Content-Length from the pivot respond since the respond body may be rewritten.
  @Override
  public void setHeader( String name, String value ) {
    if( !ignoreHeader( name) ) {
      if( rewriteHeader( name ) ) {
        value = rewriteHeaderValue( value );
      }
      super.setHeader( name, value );
    }
  }

  // Ignore the Content-Length from the pivot respond since the respond body may be rewritten.
  @Override
  public void addHeader( String name, String value ) {
    if( !ignoreHeader( name) ) {
      if( rewriteHeader( name ) ) {
        value = rewriteHeaderValue( value );
      }
      super.addHeader( name, value );
    }
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    HttpServletResponse response = (HttpServletResponse)getResponse();
    String contentType = response.getContentType();
    if( contentType != null ) {
      String[] contentTypeParts = contentType.split( ";" );
      String mediaType = contentTypeParts[ 0 ];
      if( "text/html".equals( mediaType ) ) {
        if( output == null ) {
          output = new HtmlUrlRewritingOutputStream(
              config, rewriter, response.getOutputStream(), response.getCharacterEncoding() );
        }
        return output;
      } else {
        return getResponse().getOutputStream();
      }
    } else {
      return getResponse().getOutputStream();
    }
  }

  @Override
  public Set<String> getNames() {
    return Collections.emptySet();
  }

  @Override
  @SuppressWarnings( "unchecked" )
  public List<String> getValues( String name ) {
    return Arrays.asList( config.getInitParameter( name ) );
  }
}
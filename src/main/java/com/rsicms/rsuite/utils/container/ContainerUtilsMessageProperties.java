package com.rsicms.rsuite.utils.container;

import java.io.IOException;

import com.rsicms.rsuite.utils.messsageProps.LibraryMessageProperties;

/**
 * Serves up formatted messages from this libraries message properties file.
 */
public class ContainerUtilsMessageProperties extends LibraryMessageProperties {

	public ContainerUtilsMessageProperties() throws IOException {
		super(ContainerUtilsMessageProperties.class);
	}

}

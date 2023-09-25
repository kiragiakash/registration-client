package io.mosip.registration.api.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan(basePackages = { "io.mosip.registration.ref.sarxos", "io.mosip.registration.ref.morena", "io.mosip.registration.ref.opencv" })
@Configuration
public class ScannerRefImplConfig {


}
package br.com.prognum.multi_tenants_ocr.iac;

import java.util.Map;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

public class MyApp extends App {
    public static void main(final String[] args) {
        MyApp app = new MyApp();

        String environment = (String) app.getNode().tryGetContext("environment");
        if (environment == null) {
            throw new IllegalArgumentException("Ambiente não foi passado no deploy");
        }

        Map<String, Object> envConfig = (Map<String, Object>) app.getNode().tryGetContext(environment);
        if (envConfig == null) {
            throw new RuntimeException("Configuração não encontrada para ambiente: " + environment);
        }

        String system = (String) envConfig.get("system");

        new MyStack(app, system, environment, envConfig, StackProps.builder().build());

        app.synth();
    }

}

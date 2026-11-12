package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import com.facturacion.sistemaweb.model.FacturaCliente;
import com.facturacion.sistemaweb.model.FacturaPago;
import com.facturacion.sistemaweb.model.FormaPago;

class FacturaTableMappingTest {
    @Test void preservaLosNombresExactosDeLasTablasDeFacturacion() {
        StandardServiceRegistry registry=new StandardServiceRegistryBuilder()
            .applySetting(AvailableSettings.DIALECT,"org.hibernate.dialect.SQLServerDialect")
            .applySetting(AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                CamelCaseToUnderscoresNamingStrategy.class.getName())
            .applySetting(AvailableSettings.ALLOW_METADATA_ON_BOOT,false)
            .build();
        try {
            var metadata=new MetadataSources(registry)
                .addAnnotatedClass(FacturaCliente.class)
                .addAnnotatedClass(FacturaPago.class)
                .addAnnotatedClass(FormaPago.class)
                .buildMetadata();

            assertEquals("FacturaCliente",metadata.getEntityBinding(FacturaCliente.class.getName()).getTable().getName());
            assertEquals("dbo",metadata.getEntityBinding(FacturaCliente.class.getName()).getTable().getSchema());
            assertEquals("FacturaPago",metadata.getEntityBinding(FacturaPago.class.getName()).getTable().getName());
            assertEquals("FormaPago",metadata.getEntityBinding(FormaPago.class.getName()).getTable().getName());
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}

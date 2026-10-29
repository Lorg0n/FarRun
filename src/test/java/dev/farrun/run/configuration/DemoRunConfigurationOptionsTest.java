package dev.farrun.run.configuration;

import com.intellij.util.xmlb.XmlSerializer;
import dev.farrun.run.source.SourceConfigurationReference;
import org.jdom.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DemoRunConfigurationOptionsTest {

    @Test
    void preservesTheSourceReferenceDuringXmlSerialization() {
        DemoRunConfigurationOptions options = new DemoRunConfigurationOptions();
        SourceConfigurationReference reference = new SourceConfigurationReference(
                "Application.My Application",
                "Application",
                "My Application"
        );
        options.setSourceConfigurationId(reference.settingsId());
        options.setSourceConfigurationTypeId(reference.typeId());
        options.setSourceConfigurationName(reference.name());

        Element serialized = XmlSerializer.serialize(options);
        DemoRunConfigurationOptions restored = XmlSerializer.deserialize(
                serialized,
                DemoRunConfigurationOptions.class
        );

        assertEquals(reference.settingsId(), restored.getSourceConfigurationId());
        assertEquals(reference.typeId(), restored.getSourceConfigurationTypeId());
        assertEquals(reference.name(), restored.getSourceConfigurationName());
    }

    @Test
    void rejectsAnIncompleteSourceReference() {
        assertNull(SourceConfigurationReference.from("id", "Application", null));
    }
}

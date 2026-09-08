package com.erp.oa.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;

/**
 * Opt-in locator for the controlled historical signing sample.
 *
 * <p>The sample contains personal information and must never be committed or added to a release
 * bundle.  Ordinary CI therefore exercises synthetic fixtures; a release reviewer enables this
 * local audit explicitly with {@code ERP_SIGN_GOLDEN_FIXTURE_DIR} (or the equivalent system
 * property) and records only hashes/results.</p>
 */
final class OaSignGoldenFixture
{
    static final String ENVIRONMENT_VARIABLE = "ERP_SIGN_GOLDEN_FIXTURE_DIR";
    static final String SYSTEM_PROPERTY = "erp.sign.golden.fixture.dir";
    static final String PENDING_FINAL_SHA256 =
            "bb39a7278ba042caea1ce181d40b5e6bef6096fc82b2890543dc58a23451d4cb";
    private static final List<String> REQUIRED_FILES = List.of(
            "archive.pdf", "review.pdf", "signature.png", "seal.jpg");

    private OaSignGoldenFixture() {}

    static Path requireRoot()
    {
        String configured = System.getProperty(SYSTEM_PROPERTY);
        if (configured == null || configured.isBlank())
        {
            configured = System.getenv(ENVIRONMENT_VARIABLE);
        }
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
                "CONTROLLED_GOLDEN_AUDIT_NOT_EXECUTED: set " + ENVIRONMENT_VARIABLE);
        Path root = Path.of(configured).toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isDirectory(root),
                "CONTROLLED_GOLDEN_AUDIT_NOT_EXECUTED: fixture directory is unavailable");
        for (String filename : REQUIRED_FILES)
        {
            Assumptions.assumeTrue(Files.isRegularFile(root.resolve(filename)),
                    "CONTROLLED_GOLDEN_AUDIT_NOT_EXECUTED: missing " + filename);
        }
        return root;
    }
}

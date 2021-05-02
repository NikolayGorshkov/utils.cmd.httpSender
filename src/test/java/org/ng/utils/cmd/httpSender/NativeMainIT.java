package org.ng.utils.cmd.httpSender;

import org.junit.jupiter.api.Disabled;

import io.quarkus.test.junit.NativeImageTest;

@NativeImageTest
@Disabled("Tests for native mode are not set up yet")
public class NativeMainIT extends MainTest {

    // Execute the same tests but in native mode.

    // TODO: make native image tests run, @see
    // https://github.com/quarkusio/quarkus/issues/10212

}
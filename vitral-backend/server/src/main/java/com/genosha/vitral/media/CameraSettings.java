package com.genosha.vitral.media;

/**
 * How a photo was taken, as the camera wrote it in the EXIF. Every field may be null:
 * many photos, such as screenshots or edited exports, carry none of them.
 *
 * @param make            camera maker, such as "Canon"
 * @param model           camera model, such as "Canon EOS R6"; phones often repeat the maker here
 * @param lens            lens model, such as "RF24-105mm F4 L IS USM"
 * @param fNumber         aperture, such as 4.0 for f/4
 * @param exposureSeconds shutter time in seconds, such as 0.004 for 1/250 s
 * @param iso             sensor sensitivity, such as 400
 * @param focalLength     focal length in millimeters, such as 50.0
 */
public record CameraSettings(String make, String model, String lens, Double fNumber, Double exposureSeconds,
                             Integer iso, Double focalLength) {

    static final CameraSettings NONE = new CameraSettings(null, null, null, null, null, null, null);
}

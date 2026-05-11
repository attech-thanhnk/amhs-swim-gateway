/**
 * Copyright (C) 2018 Dmitry Moryakov, Main aeronautical meteorological center, Moscow, Russia
 * moryakovdv[at]gmail[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vn.asg.converter.iwxxm;

import vn.asg.converter.common.CommonRegex;
import vn.asg.converter.exceptions.UnSupportFormatException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gamc.spmi.iwxxmConverter.exceptions.ParsingException;
import org.gamc.spmi.iwxxmConverter.tac.TacConverter;

public class ConverterFactory {

    /**
     * Factory produces converter for message using the start token of the input string
     */
    public final static Pattern messageTypePattern = Pattern.compile("(?<messageType>METAR|TAF|SPECI|SIGMET|AIRMET|SWX(?=\\s+ADVISORY))");

    public static TacConverter<?, ?> createForTac(String inputTac) throws ParsingException {

        Matcher m = messageTypePattern.matcher(inputTac);
        if (!m.find()) {
            throw new ParsingException("Can not determine message type");
        }

        String messageType = m.group("messageType").toUpperCase();

        switch (messageType) {
            case "METAR":
                return new METARConverterV3();

            case "SPECI":
                return new SPECIConverterV3();

            case "TAF":
                return new TAFConverterV3();

            case "SIGMET":
                return new SIGMETConverterV3();

            default:
                throw new ParsingException("Can not determine message type");

        }
    }

    private static TacBaseConverter getConverter(String tac) throws UnSupportFormatException {
        Matcher matcher = CommonRegex.metarBulletin.matcher(tac);
        if (matcher.lookingAt()) {
            return new METARBulletinConverterV3();
        }

        // Process METAR
        matcher = CommonRegex.metarToken.matcher(tac);
        if (matcher.lookingAt()) {
            return new METARConverterV3();
        }

        // Process SPECI BULLETIN
        matcher = CommonRegex.speciBulletin.matcher(tac);
        if (matcher.lookingAt()) {
            return new SPECIBulletinConverterV3();
        }

        // Process SPECI
        matcher = CommonRegex.speciToken.matcher(tac);
        if (matcher.lookingAt()) {
            return new SPECIConverterV3();
        }

        // Process TAF BULLETIN
        matcher = CommonRegex.tafBulletin.matcher(tac);
        if (matcher.lookingAt()) {
            return new TAFBulletinConvertV3();
        }

        // Process TAF
        matcher = CommonRegex.tafToken.matcher(tac);
        if (matcher.lookingAt()) {
            return new TAFConverterV3();
        }

        // Process SIGMET BULLETIN
        matcher = CommonRegex.sigmetBulletin.matcher(tac);
        if (matcher.lookingAt()) {
            return new SIGMETBulletinConverterV3();
        }

        // Process SIGMET
        matcher = CommonRegex.sigmetToken.matcher(tac);
        if (matcher.lookingAt()) {
            return new SIGMETConverterV3();
        }

        throw new UnSupportFormatException();
    }

}



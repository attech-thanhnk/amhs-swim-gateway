package vn.asg.converter.builder;

import aero.fixm.flight._4_2.*;
import vn.asg.converter.model.FplMessage;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Xây dựng XML FIXM 4.2.
 * Hỗ trợ: FPL, CHG, CNL, DEP, ARR, DLA.
 */
public class FixmBuilder {

    public String buildFpl(FplMessage msg) {
        try {
            ObjectFactory f = new ObjectFactory();
            FlightType flight = f.createFlightType();

            // ── Flight Identification (Field 7: Callsign) ──
            if (notBlank(msg.getAircraftId())) {
                FlightIdentificationType flightId = f.createFlightIdentificationType();
                flightId.setAircraftIdentification(
                        f.createFlightIdentificationTypeAircraftIdentification(msg.getAircraftId()));
                flight.setFlightIdentification(f.createFlightTypeFlightIdentification(flightId));
            }

            // ── Type of Flight (Field 8: S/N/G/M/X) ──
            // FlightRulesCategory (I/V/Y/Z) thuộc về RouteInformation, không phải
            // FlightType trực tiếp.
            // TypeOfFlight (S/N/G/M/X) thuộc FlightType.
            if (notBlank(msg.getFlightType())) {
                TypeOfFlightType flightType = mapFlightType(msg.getFlightType());
                if (flightType != null) {
                    flight.setFlightType(f.createFlightTypeFlightType(flightType));
                }
            }

            // ── Departure (Field 13) ──
            DepartureType departure = f.createDepartureType();
            if (notBlank(msg.getDepartureIcao())) {
                AerodromeReferenceType depAero = f.createAerodromeReferenceType();
                depAero.setLocationIndicator(
                        f.createAerodromeReferenceTypeLocationIndicator(msg.getDepartureIcao()));
                departure.setAerodrome(f.createDepartureTypeAerodrome(depAero));
            }

            if (notBlank(msg.getDepartureTime())) {
                XMLGregorianCalendar cal = convertToIsoTime(msg.getDepartureTime(), msg.getDof());
                if (cal != null) {
                    if ("DEP".equals(msg.getMessageType())) {
                        // Cất cánh thực tế
                        departure.setActualTimeOfDeparture(
                                f.createDepartureTypeActualTimeOfDeparture(cal));
                    } else {
                        // EOBT cho FPL/CHG/CNL/DLA
                        departure.setEstimatedOffBlockTime(
                                f.createDepartureTypeEstimatedOffBlockTime(cal));
                    }
                }
            }
            flight.setDeparture(f.createFlightTypeDeparture(departure));

            // ── Arrival (Field 16) ──
            ArrivalType arrival = f.createArrivalType();
            if (notBlank(msg.getDestinationIcao())) {
                AerodromeReferenceType destAero = f.createAerodromeReferenceType();
                destAero.setLocationIndicator(
                        f.createAerodromeReferenceTypeLocationIndicator(msg.getDestinationIcao()));
                arrival.setDestinationAerodrome(f.createArrivalTypeDestinationAerodrome(destAero));
            }

            if (notBlank(msg.getTotalEet())) {
                if ("ARR".equals(msg.getMessageType())) {
                    // Hạ cánh thực tế
                    XMLGregorianCalendar arrCal = convertToIsoTime(msg.getTotalEet(), msg.getDof());
                    if (arrCal != null) {
                        arrival.setActualTimeOfArrival(
                                f.createArrivalTypeActualTimeOfArrival(arrCal));
                    }
                }
            }

            // Sân bay dự bị: thêm trực tiếp vào List (không cần factory method)
            if (notBlank(msg.getAltDestination1())) {
                AerodromeReferenceType alt1 = f.createAerodromeReferenceType();
                alt1.setLocationIndicator(
                        f.createAerodromeReferenceTypeLocationIndicator(msg.getAltDestination1()));
                arrival.getDestinationAerodromeAlternate().add(alt1);
            }
            if (notBlank(msg.getAltDestination2())) {
                AerodromeReferenceType alt2 = f.createAerodromeReferenceType();
                alt2.setLocationIndicator(
                        f.createAerodromeReferenceTypeLocationIndicator(msg.getAltDestination2()));
                arrival.getDestinationAerodromeAlternate().add(alt2);
            }
            flight.setArrival(f.createFlightTypeArrival(arrival));

            // ── Route & Speed/Level (Field 15) ──
            if (notBlank(msg.getRoute()) || notBlank(msg.getCruisingSpeed())) {
                RouteTrajectoryGroupContainerType routeContainer = f.createRouteTrajectoryGroupContainerType();
                RouteTrajectoryGroupType routeGroup = f.createRouteTrajectoryGroupType();
                FlightRouteInformationType routeInfo = f.createFlightRouteInformationType();

                // Route text
                if (notBlank(msg.getRoute())) {
                    routeInfo.setRouteText(
                            f.createFlightRouteInformationTypeRouteText(buildRouteText(msg)));
                }

                // Cruising Speed — TrueAirspeedType
                if (notBlank(msg.getCruisingSpeed())) {
                    TrueAirspeedType speed = buildTrueAirspeed(f, msg.getCruisingSpeed());
                    if (speed != null) {
                        routeInfo.setCruisingSpeed(
                                f.createFlightRouteInformationTypeCruisingSpeed(speed));
                    }
                }

                // Cruising Level — FlightLevelOrAltitudeChoiceType
                if (notBlank(msg.getCruisingLevel())) {
                    FlightLevelOrAltitudeChoiceType level = buildFlightLevel(f, msg.getCruisingLevel());
                    if (level != null) {
                        routeInfo.setCruisingLevel(
                                f.createFlightRouteInformationTypeCruisingLevel(level));
                    }
                }

                // Flight Rules Category (I/V/Y/Z) thuộc RouteInformation trong FIXM 4.2
                if (notBlank(msg.getFlightRules())) {
                    FlightRulesCategoryType frc = mapFlightRulesCategory(msg.getFlightRules());
                    if (frc != null) {
                        routeInfo.setFlightRulesCategory(
                                f.createFlightRouteInformationTypeFlightRulesCategory(frc));
                    }
                }

                // EET dạng Duration (PT##H##M) cho FPL
                if (!"ARR".equals(msg.getMessageType()) && notBlank(msg.getTotalEet())) {
                    Duration dur = convertEetToDuration(msg.getTotalEet());
                    if (dur != null) {
                        routeInfo.setTotalEstimatedElapsedTime(
                                f.createFlightRouteInformationTypeTotalEstimatedElapsedTime(dur));
                    }
                }

                routeGroup.setRouteInformation(
                        f.createRouteTrajectoryGroupTypeRouteInformation(routeInfo));
                routeContainer.setFiled(
                        f.createRouteTrajectoryGroupContainerTypeFiled(routeGroup));
                flight.setRouteTrajectoryGroup(
                        f.createFlightTypeRouteTrajectoryGroup(routeContainer));
            }

            // ── Aircraft (Field 9) ──
            AircraftType aircraft = f.createAircraftType();
            if (notBlank(msg.getAircraftType())) {
                AircraftTypeType att = f.createAircraftTypeType();
                AircraftTypeChoiceType atc = f.createAircraftTypeChoiceType();
                atc.setIcaoAircraftTypeDesignator(msg.getAircraftType());
                att.setType(f.createAircraftTypeTypeType(atc));
                aircraft.getAircraftType().add(att);
            }

            // Wake turbulence
            if (notBlank(msg.getWakeTurbulence())) {
                WakeTurbulenceCategoryType wake = mapWakeTurbulence(msg.getWakeTurbulence());
                aircraft.setWakeTurbulence(f.createAircraftTypeWakeTurbulence(wake));
            }

            // Registration
            if (notBlank(msg.getRegistration())) {
                aircraft.getRegistration().add(
                        f.createAircraftTypeRegistration(java.util.Collections.singletonList(msg.getRegistration())));
            }
            flight.setAircraft(f.createFlightTypeAircraft(aircraft));

            // ── Capabilities (Field 10) ──
            // FIXM 4.2: FlightCapabilitiesType không có setSurveillanceCapabilities(String)
            // Dùng SurveillanceCapabilitiesType với typed List
            if (notBlank(msg.getEquipment())) {
                FlightCapabilitiesType caps = f.createFlightCapabilitiesType();
                String equip = msg.getEquipment();
                String[] equipParts = equip.split("/", 2);

                // Navigation/Comm capabilities indicator
                if (equipParts.length > 0 && notBlank(equipParts[0])) {
                    String commNavCodes = equipParts[0].trim();
                    if ("S".equalsIgnoreCase(commNavCodes)) {
                        caps.setStandardCapabilities(
                                f.createFlightCapabilitiesTypeStandardCapabilities(
                                        StandardCapabilitiesIndicatorType.STANDARD));
                    } else {
                        // Non-standard equipment codes (e.g., SDE2E3FGHIRWY)
                        // Store in navigation capabilities
                        NavigationCapabilitiesType navCap = f.createNavigationCapabilitiesType();
                        navCap.setOtherNavigationCapabilities(
                                f.createNavigationCapabilitiesTypeOtherNavigationCapabilities(commNavCodes));
                        caps.setNavigation(f.createFlightCapabilitiesTypeNavigation(navCap));
                    }
                }

                // Surveillance capabilities (SSR: A/C/S/...)
                if (equipParts.length > 1 && notBlank(equipParts[1])) {
                    SurveillanceCapabilitiesType surv = f.createSurveillanceCapabilitiesType();
                    // SurveillanceCapabilitiesType lưu danh sách code
                    // Tạm ghi vào SurveillanceCapabilitiesCode list
                    parseSurveillanceCodes(f, equipParts[1], surv);
                    caps.setSurveillance(
                            f.createFlightCapabilitiesTypeSurveillance(surv));
                }
                aircraft.setCapabilities(f.createAircraftTypeCapabilities(caps));
            }

            // ── Supplementary Data (Field 18 items) ──
            SupplementaryDataType supp = f.createSupplementaryDataType();
            boolean hasSuppData = false;

            // Operator (OPR/)
            if (notBlank(msg.getOperator())) {
                AircraftOperatorType opr = f.createAircraftOperatorType();
                PersonOrOrganizationType po = new PersonOrOrganizationType();
                po.setName(f.createPersonOrOrganizationTypeName(msg.getOperator()));
                opr.setOperatingOrganization(f.createAircraftOperatorTypeOperatingOrganization(po));
                flight.setOperator(f.createFlightTypeOperator(opr));
            }

            // Remarks (RMK/)
            if (notBlank(msg.getRemarks())) {
                flight.setRemarks(f.createFlightTypeRemarks(msg.getRemarks()));
            }

            // PBN capabilities (PBN/) - append to existing navigation capabilities
            if (notBlank(msg.getPbn())) {
                if (aircraft.getCapabilities() != null && aircraft.getCapabilities().getValue() != null) {
                    FlightCapabilitiesType caps = aircraft.getCapabilities().getValue();
                    if (caps.getNavigation() != null && caps.getNavigation().getValue() != null) {
                        NavigationCapabilitiesType navCap = caps.getNavigation().getValue();
                        String existing = "";
                        if (navCap.getOtherNavigationCapabilities() != null &&
                                navCap.getOtherNavigationCapabilities().getValue() != null) {
                            existing = navCap.getOtherNavigationCapabilities().getValue();
                        }
                        // Append PBN codes
                        String combined = existing.isEmpty() ? msg.getPbn() : existing + " PBN/" + msg.getPbn();
                        navCap.setOtherNavigationCapabilities(
                                f.createNavigationCapabilitiesTypeOtherNavigationCapabilities(combined));
                    } else {
                        NavigationCapabilitiesType navCap = f.createNavigationCapabilitiesType();
                        navCap.setOtherNavigationCapabilities(
                                f.createNavigationCapabilitiesTypeOtherNavigationCapabilities("PBN/" + msg.getPbn()));
                        caps.setNavigation(f.createFlightCapabilitiesTypeNavigation(navCap));
                    }
                }
            }

            // SELCAL (SEL/)
            if (notBlank(msg.getSelcal())) {
                CommunicationCapabilitiesType commCap = f.createCommunicationCapabilitiesType();
                commCap.setSelectiveCallingCode(
                        f.createCommunicationCapabilitiesTypeSelectiveCallingCode(msg.getSelcal()));
                if (aircraft.getCapabilities() == null || aircraft.getCapabilities().getValue() == null) {
                    FlightCapabilitiesType caps = f.createFlightCapabilitiesType();
                    aircraft.setCapabilities(f.createAircraftTypeCapabilities(caps));
                }
                aircraft.getCapabilities().getValue().setCommunication(
                        f.createFlightCapabilitiesTypeCommunication(commCap));
            }

            // STS (Special handling)
            if (notBlank(msg.getSts())) {
                String currentRemarks = "";
                if (flight.getRemarks() != null && flight.getRemarks().getValue() != null) {
                    currentRemarks = flight.getRemarks().getValue();
                }
                String stsRemark = currentRemarks.isEmpty() ? "STS/" + msg.getSts()
                        : currentRemarks + " STS/" + msg.getSts();
                flight.setRemarks(f.createFlightTypeRemarks(stsRemark));
            }

            // Fuel Endurance & Persons on board → supp data
            if (hasSuppData) {
                flight.setSupplementaryData(f.createFlightTypeSupplementaryData(supp));
            }

            // ── Marshal to XML ──
            JAXBContext context = JAXBContext.newInstance(FlightType.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            // Tạo root element <fx:Flight>
            JAXBElement<FlightType> root = f.createFlight(flight);
            StringWriter writer = new StringWriter();
            marshaller.marshal(root, writer);
            return writer.toString();

        } catch (Exception e) {
            throw new RuntimeException("Error building FIXM XML: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // Helper: Cruising Speed → TrueAirspeedType
    // N0450 → {uom=KT, value=450}
    // K0720 → {uom=KM_H, value=720}
    // M082 → {uom=MACH, value=0.82}
    // ─────────────────────────────────────────────────
    private TrueAirspeedType buildTrueAirspeed(ObjectFactory f, String speedStr) {
        if (speedStr == null || speedStr.length() < 4)
            return null;
        try {
            TrueAirspeedType speed = f.createTrueAirspeedType();
            char unit = speedStr.charAt(0);
            String val = speedStr.substring(1);
            switch (unit) {
                case 'N' -> {
                    speed.setUom(UomAirspeedType.KT);
                    speed.setValue(Double.parseDouble(val));
                }
                case 'K' -> {
                    speed.setUom(UomAirspeedType.KM_H);
                    speed.setValue(Double.parseDouble(val));
                }
                case 'M' -> {
                    // M082 → 0.82 Mach
                    speed.setUom(UomAirspeedType.MACH);
                    speed.setValue(Double.parseDouble(val) / 100.0);
                }
                default -> {
                    return null;
                }
            }
            return speed;
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────
    // Helper: Cruising Level → FlightLevelOrAltitudeChoiceType
    // F330 → Flight Level 330
    // A100 → Altitude 10000 ft
    // S1300 → Metric level 13000 m
    // ─────────────────────────────────────────────────
    private FlightLevelOrAltitudeChoiceType buildFlightLevel(ObjectFactory f, String levelStr) {
        if (levelStr == null || levelStr.length() < 4)
            return null;
        try {
            FlightLevelOrAltitudeChoiceType level = f.createFlightLevelOrAltitudeChoiceType();
            char unit = levelStr.charAt(0);
            String val = levelStr.substring(1);
            int numVal = Integer.parseInt(val);
            switch (unit) {
                case 'F' -> {
                    FlightLevelType fl = f.createFlightLevelType();
                    fl.setUom(UomFlightLevelType.FL);
                    fl.setValue((double) numVal);
                    level.setFlightLevel(fl);
                }
                case 'A' -> {
                    AltitudeType alt = f.createAltitudeType();
                    alt.setUom(UomAltitudeType.FT);
                    alt.setValue((double) (numVal * 100));
                    level.setAltitude(alt);
                }
                case 'S', 'M' -> {
                    AltitudeType alt = f.createAltitudeType();
                    alt.setUom(UomAltitudeType.M);
                    alt.setValue((double) (numVal * 10));
                    level.setAltitude(alt);
                }
                default -> {
                    return null;
                }
            }
            return level;
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────
    // Helper: Parse surveillance codes (A/C/S/L/B1...)
    // ─────────────────────────────────────────────────
    private void parseSurveillanceCodes(ObjectFactory f, String codes, SurveillanceCapabilitiesType surv) {
        // Các mã SSR phổ biến trong ICAO Doc 4444:
        // A = Mode A (4096 codes) C = Mode C (altitude)
        // S = Mode S (Elementary) E = Mode S (Enhanced)
        // L = ADS-B (1090MHz In) B1 = ADS-B (1090MHz Out, DO-260A)
        // Mapping sang SurveillanceCapabilityCodeType enum
        if (codes == null || codes.isEmpty())
            return;
        // SurveillanceCapabilitiesType dùng
        // List<JAXBElement<List<SurveillanceCapabilityCodeType>>>
        // Tạm thời set otherSurveillanceCapabilities để lưu raw string
        surv.setOtherSurveillanceCapabilities(
                f.createSurveillanceCapabilitiesTypeOtherSurveillanceCapabilities(codes.trim()));
    }

    // ─────────────────────────────────────────────────
    // Helper: Build full route text (Speed+Level+Route)
    // ─────────────────────────────────────────────────
    private String buildRouteText(FplMessage msg) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(msg.getCruisingSpeed()))
            sb.append(msg.getCruisingSpeed());
        if (notBlank(msg.getCruisingLevel()))
            sb.append(msg.getCruisingLevel());
        if (notBlank(msg.getRoute())) {
            if (!sb.isEmpty())
                sb.append(" ");
            sb.append(msg.getRoute());
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────
    // Helper: AFTN time HHMM → XMLGregorianCalendar
    // ─────────────────────────────────────────────────
    private XMLGregorianCalendar convertToIsoTime(String hhmm, String dof) {
        if (hhmm == null || hhmm.length() != 4)
            return null;
        try {
            GregorianCalendar gc = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            if (notBlank(dof) && dof.length() == 6) {
                gc.set(GregorianCalendar.YEAR, 2000 + Integer.parseInt(dof.substring(0, 2)));
                gc.set(GregorianCalendar.MONTH, Integer.parseInt(dof.substring(2, 4)) - 1);
                gc.set(GregorianCalendar.DAY_OF_MONTH, Integer.parseInt(dof.substring(4, 6)));
            }
            gc.set(GregorianCalendar.HOUR_OF_DAY, Integer.parseInt(hhmm.substring(0, 2)));
            gc.set(GregorianCalendar.MINUTE, Integer.parseInt(hhmm.substring(2, 4)));
            gc.set(GregorianCalendar.SECOND, 0);
            gc.set(GregorianCalendar.MILLISECOND, 0);
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────
    // Helper: EET HHMM → javax.xml.datatype.Duration
    // "0205" → PT2H5M
    // ─────────────────────────────────────────────────
    private Duration convertEetToDuration(String eet) {
        if (eet == null || eet.length() != 4)
            return null;
        try {
            int hh = Integer.parseInt(eet.substring(0, 2));
            int mm = Integer.parseInt(eet.substring(2, 4));
            return DatatypeFactory.newInstance().newDurationYearMonth(
                    true, 0, 0).add(
                            DatatypeFactory.newInstance().newDuration(
                                    true, 0, 0, 0, hh, mm, 0));
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────
    // Helper: Map Flight Rules (I/V/Y/Z) → FIXM enum
    // ─────────────────────────────────────────────────
    private FlightRulesCategoryType mapFlightRulesCategory(String rules) {
        if (rules == null)
            return null;
        return switch (rules.toUpperCase()) {
            case "I" -> FlightRulesCategoryType.I;
            case "V" -> FlightRulesCategoryType.V;
            case "Y" -> FlightRulesCategoryType.Y;
            case "Z" -> FlightRulesCategoryType.Z;
            default -> null;
        };
    }

    // ─────────────────────────────────────────────────
    // Helper: Map Type of Flight (S/N/G/M/X) → FIXM enum
    // ─────────────────────────────────────────────────
    private TypeOfFlightType mapFlightType(String type) {
        if (type == null)
            return null;
        return switch (type.toUpperCase()) {
            case "S" -> TypeOfFlightType.S;
            case "N" -> TypeOfFlightType.N;
            case "G" -> TypeOfFlightType.G;
            case "M" -> TypeOfFlightType.M;
            case "X" -> TypeOfFlightType.X;
            default -> null;
        };
    }

    // ─────────────────────────────────────────────────
    // Helper: Map Wake Turbulence (H/M/L/J) → FIXM enum
    // ─────────────────────────────────────────────────
    private WakeTurbulenceCategoryType mapWakeTurbulence(String wake) {
        if (wake == null)
            return WakeTurbulenceCategoryType.M;
        return switch (wake.toUpperCase()) {
            case "H" -> WakeTurbulenceCategoryType.H;
            case "M" -> WakeTurbulenceCategoryType.M;
            case "L" -> WakeTurbulenceCategoryType.L;
            case "J" -> WakeTurbulenceCategoryType.J;
            default -> WakeTurbulenceCategoryType.M;
        };
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

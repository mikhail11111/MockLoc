using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;

namespace MockLocWin
{
    /// <summary>
    /// Detects the current country from the public IP (follows VPN, if any)
    /// and resolves coordinates inside that country. Keyless HTTPS endpoints.
    /// </summary>
    public static class IpCountry
    {
        public class IpGeo
        {
            public string Ip = "";
            public string Country = "";
            public string CountryCode = "";
            public string City = "";
            public double? Lat;
            public double? Lng;

            public (double lat, double lng)? ResolveLatLng()
            {
                if (Lat.HasValue && Lng.HasValue && Lat >= -90 && Lat <= 90 && Lng >= -180 && Lng <= 180)
                    return (Lat.Value, Lng.Value);
                if (!string.IsNullOrEmpty(CountryCode) && Capitals.TryGetValue(CountryCode.ToUpperInvariant(), out var c))
                    return c;
                return null;
            }

            public string Label()
            {
                if (!string.IsNullOrEmpty(City) && !string.IsNullOrEmpty(Country)) return City + ", " + Country;
                return City + Country;
            }
        }

        public static async Task<IpGeo> FetchAsync()
        {
            using (var http = new HttpClient { Timeout = TimeSpan.FromSeconds(12) })
            {
                http.DefaultRequestHeaders.UserAgent.ParseAdd("MockLoc-App");
                Exception first = null;
                try { return ParseIpWho(await http.GetStringAsync("https://ipwho.is/")); }
                catch (Exception ex) { first = ex; }
                try { return ParseFreeIpApi(await http.GetStringAsync("https://freeipapi.com/api/json")); }
                catch (Exception ex) { throw new Exception("ipwho.is: " + first?.Message + "; freeipapi: " + ex.Message); }
            }
        }

        // Synchronous wrapper for Task.Run usage
        public static IpGeo Fetch() => FetchAsync().GetAwaiter().GetResult();

        private static IpGeo ParseIpWho(string body)
        {
            using (var doc = JsonDocument.Parse(body))
            {
                var r = doc.RootElement;
                if (!r.TryGetProperty("success", out var ok) || !ok.GetBoolean())
                    throw new Exception(r.TryGetProperty("message", out var m) ? m.GetString() : "lookup failed");
                return new IpGeo
                {
                    Ip = GetStr(r, "ip"),
                    Country = GetStr(r, "country"),
                    CountryCode = GetStr(r, "country_code"),
                    City = GetStr(r, "city"),
                    Lat = GetDbl(r, "latitude"),
                    Lng = GetDbl(r, "longitude")
                };
            }
        }

        private static IpGeo ParseFreeIpApi(string body)
        {
            using (var doc = JsonDocument.Parse(body))
            {
                var r = doc.RootElement;
                return new IpGeo
                {
                    Ip = GetStr(r, "ipAddress"),
                    Country = GetStr(r, "countryName"),
                    CountryCode = GetStr(r, "countryCode"),
                    City = GetStr(r, "cityName"),
                    Lat = GetDbl(r, "latitude"),
                    Lng = GetDbl(r, "longitude")
                };
            }
        }

        private static string GetStr(JsonElement r, string name) =>
            r.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() ?? "" : "";

        private static double? GetDbl(JsonElement r, string name)
        {
            if (r.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.Number) return v.GetDouble();
            return null;
        }

        // Fallback: capital coordinates per ISO country code.
        private static readonly Dictionary<string, (double, double)> Capitals =
            new Dictionary<string, (double, double)>
        {
            {"US", (38.9072, -77.0369)}, {"CA", (45.4215, -75.6972)}, {"MX", (19.4326, -99.1332)},
            {"BR", (-15.7939, -47.8828)}, {"AR", (-34.6037, -58.3816)}, {"CL", (-33.4489, -70.6693)},
            {"CO", (4.711, -74.0721)}, {"PE", (-12.0464, -77.0428)}, {"VE", (10.4806, -66.9036)},
            {"GB", (51.5074, -0.1278)}, {"IE", (53.3498, -6.2603)}, {"FR", (48.8566, 2.3522)},
            {"DE", (52.52, 13.405)}, {"ES", (40.4168, -3.7038)}, {"PT", (38.7223, -9.1393)},
            {"IT", (41.9028, 12.4964)}, {"NL", (52.3676, 4.9041)}, {"BE", (50.8503, 4.3517)},
            {"LU", (49.6116, 6.1319)}, {"CH", (46.948, 7.4474)}, {"AT", (48.2082, 16.3738)},
            {"MC", (43.7384, 7.4246)}, {"AD", (42.5063, 1.5218)}, {"MT", (35.8989, 14.5146)},
            {"CY", (35.1856, 33.3823)}, {"SE", (59.3293, 18.0686)}, {"NO", (59.9139, 10.7522)},
            {"DK", (55.6761, 12.508)}, {"FI", (60.1699, 24.9384)}, {"IS", (64.1466, -21.9426)},
            {"EE", (59.437, 24.7536)}, {"LV", (56.9496, 24.1052)}, {"LT", (54.6872, 25.2797)},
            {"PL", (52.2297, 21.0122)}, {"CZ", (50.0755, 14.4378)}, {"SK", (48.1486, 17.1077)},
            {"HU", (47.4979, 19.0402)}, {"RO", (44.4268, 26.1025)}, {"BG", (42.6977, 23.3219)},
            {"GR", (37.9838, 23.7275)}, {"HR", (45.815, 15.9819)}, {"SI", (46.0569, 14.5058)},
            {"BA", (43.8563, 18.4131)}, {"RS", (44.7866, 20.4489)}, {"ME", (42.4304, 19.2594)},
            {"MK", (42.0029, 21.4254)}, {"AL", (41.3275, 19.8187)}, {"XK", (42.6629, 21.1652)},
            {"UA", (50.4501, 30.5234)}, {"BY", (53.9045, 27.5615)}, {"MD", (47.0105, 28.8638)},
            {"RU", (55.7558, 37.6173)}, {"KZ", (51.1694, 71.4491)}, {"KG", (42.8746, 74.5698)},
            {"UZ", (41.3111, 69.2797)}, {"TJ", (38.5598, 68.787)}, {"TM", (37.9601, 58.3265)},
            {"TR", (39.9334, 32.8597)}, {"GE", (41.7151, 44.8271)}, {"AM", (40.1792, 44.4991)},
            {"AZ", (40.4093, 49.8671)}, {"IL", (31.7683, 35.2137)}, {"JO", (31.9539, 35.9106)},
            {"LB", (33.8938, 35.5018)}, {"SY", (33.5138, 36.2765)}, {"IQ", (33.3152, 44.3661)},
            {"IR", (35.6892, 51.389)}, {"SA", (24.7136, 46.6753)}, {"AE", (24.4539, 54.3773)},
            {"QA", (25.2854, 51.531)}, {"KW", (29.3759, 47.9774)}, {"BH", (26.2285, 50.586)},
            {"OM", (23.588, 58.3829)}, {"YE", (15.3694, 44.191)}, {"AF", (34.5553, 69.2075)},
            {"PK", (33.6844, 73.0479)}, {"IN", (28.6139, 77.209)}, {"BD", (23.8103, 90.4125)},
            {"LK", (6.9271, 79.8612)}, {"NP", (27.7172, 85.324)}, {"MM", (19.7633, 96.0785)},
            {"TH", (13.7563, 100.5018)}, {"VN", (21.0278, 105.8342)}, {"KH", (11.55, 104.9167)},
            {"LA", (17.9757, 102.6331)}, {"MY", (3.139, 101.6869)}, {"SG", (1.3521, 103.8198)},
            {"ID", (-6.2088, 106.8456)}, {"PH", (14.5995, 120.9842)}, {"CN", (39.9042, 116.4074)},
            {"HK", (22.3193, 114.1694)}, {"TW", (25.033, 121.5654)}, {"JP", (35.6762, 139.6503)},
            {"KR", (37.5665, 126.978)}, {"KP", (39.0392, 125.7545)}, {"MN", (47.8864, 106.9057)},
            {"AU", (-35.2809, 149.13)}, {"NZ", (-41.2865, 174.7762)}, {"FJ", (-18.1416, 178.4419)},
            {"EG", (30.0444, 31.2357)}, {"LY", (32.8872, 13.1913)}, {"TN", (36.8065, 10.1815)},
            {"DZ", (36.7538, 3.0588)}, {"MA", (34.0209, -6.8416)}, {"NG", (9.0579, 7.4951)},
            {"GH", (5.6037, -0.187)}, {"KE", (1.2921, 36.8219)}, {"ET", (9.0054, 38.7636)},
            {"ZA", (-26.2041, 28.0473)}
        };
    }
}

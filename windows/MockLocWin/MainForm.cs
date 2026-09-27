using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Threading.Tasks;
using System.Windows.Forms;
using Windows.Devices.Geolocation;

namespace MockLocWin
{
    public class MainForm : Form
    {
        private readonly TextBox txtLat = new TextBox();
        private readonly TextBox txtLng = new TextBox();
        private readonly TextBox txtStatus = new TextBox();

        public MainForm()
        {
            Text = "MockLoc for Windows";
            ClientSize = new Size(480, 600);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;

            int y = 12, pad = 12, gap = 8;

            txtStatus.Multiline = true;
            txtStatus.ReadOnly = true;
            txtStatus.ScrollBars = ScrollBars.Vertical;
            txtStatus.SetBounds(pad, y, 456, 170);
            Controls.Add(txtStatus);
            y += 170 + gap;

            var lblLat = new Label { Text = "Latitude (-90 … 90):", AutoSize = true };
            lblLat.SetBounds(pad, y, 200, 20);
            Controls.Add(lblLat);
            y += 22;
            txtLat.SetBounds(pad, y, 456, 28);
            Controls.Add(txtLat);
            y += 28 + gap;

            var lblLng = new Label { Text = "Longitude (-180 … 180):", AutoSize = true };
            lblLng.SetBounds(pad, y, 200, 20);
            Controls.Add(lblLng);
            y += 22;
            txtLng.SetBounds(pad, y, 456, 28);
            Controls.Add(txtLng);
            y += 28 + gap;

            y = AddButton("Set as Windows default location", y, BtnSet_Click);
            y = AddButton("Clear default location", y, BtnClear_Click);
            y = AddButton("Show current (default + effective)", y, BtnShow_Click);
            y = AddButton("Set location from IP country", y, BtnIp_Click);
            y = AddButton("Open Location Settings", y, BtnSettings_Click);

            var hint = new Label
            {
                Text = "Presets: NYC 40.712776,-74.005974 • London 51.507351,-0.127758 • Tokyo 35.6762,139.6503",
                AutoSize = false
            };
            hint.SetBounds(pad, y, 456, 40);
            Controls.Add(hint);

            Load += async (_, __) => { LoadLast(); await RefreshStatus(); };
        }

        private int AddButton(string text, int y, EventHandler onClick)
        {
            var b = new Button { Text = text };
            b.SetBounds(12, y, 456, 34);
            b.Click += onClick;
            Controls.Add(b);
            return y + 34 + 8;
        }

        private string SettingsPath()
        {
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "MockLocWin");
            Directory.CreateDirectory(dir);
            return Path.Combine(dir, "last.txt");
        }

        private void LoadLast()
        {
            try
            {
                var parts = File.ReadAllText(SettingsPath()).Split(',');
                if (parts.Length == 2) { txtLat.Text = parts[0].Trim(); txtLng.Text = parts[1].Trim(); }
            }
            catch { /* first run */ }
        }

        private void SaveLast(double lat, double lng)
        {
            try { File.WriteAllText(SettingsPath(), lat + "," + lng); } catch { }
        }

        private void Status(string s) { txtStatus.Text = s; }

        private static string AccessGuidance() =>
            "Location access denied.\r\n" +
            "1. Open Location Settings (button below).\r\n" +
            "2. Turn ON location for the device and the user.\r\n" +
            "3. Turn ON 'Let desktop apps access your location'.\r\n" +
            "(The device-wide switch needs an administrator.)";

        private async Task<bool> EnsureAccess()
        {
            try
            {
                var access = await Geolocator.RequestAccessAsync().AsTask();
                if (access == GeolocationAccessStatus.Allowed) return true;
                Status("Location permission: " + access + "\r\n\r\n" + AccessGuidance());
                return false;
            }
            catch (UnauthorizedAccessException)
            {
                Status(AccessGuidance());
                return false;
            }
            catch (Exception ex)
            {
                Status("Access check failed: " + ex.Message);
                return false;
            }
        }

        private async void BtnSet_Click(object sender, EventArgs e)
        {
            if (!TryParse(out double lat, out double lng)) return;
            if (!await EnsureAccess()) return;
            try
            {
                Geolocator.DefaultGeoposition = new BasicGeoposition
                {
                    Latitude = lat,
                    Longitude = lng,
                    Altitude = 0
                };
                SaveLast(lat, lng);
                Status("Default location set to:\r\n" + lat + ", " + lng +
                    "\r\n\r\nWindows, Edge and apps using the Windows location API will use it " +
                    "when no more exact source (GPS/Wi-Fi) is available. It persists across reboots.");
            }
            catch (UnauthorizedAccessException)
            {
                Status(AccessGuidance());
            }
            catch (Exception ex)
            {
                Status("Failed to set default location:\r\n" + ex.Message);
            }
        }

        private async void BtnClear_Click(object sender, EventArgs e)
        {
            if (!await EnsureAccess()) return;
            try
            {
                Geolocator.DefaultGeoposition = null;
                Status("Default location cleared. Apps fall back to live sources again.");
            }
            catch (UnauthorizedAccessException)
            {
                Status(AccessGuidance());
            }
            catch (Exception ex)
            {
                Status("Failed to clear default location:\r\n" + ex.Message);
            }
        }

        private async void BtnShow_Click(object sender, EventArgs e)
        {
            await RefreshStatus();
        }

        private async Task RefreshStatus()
        {
            var sb = new System.Text.StringBuilder();
            try
            {
                var def = Geolocator.DefaultGeoposition;
                sb.Append("Stored default: ");
                sb.Append(def == null ? "(none)" : def.Value.Latitude + ", " + def.Value.Longitude);
                sb.Append("\r\nRecommend setting one: " + Geolocator.IsDefaultGeopositionRecommended);
            }
            catch (UnauthorizedAccessException)
            {
                Status(AccessGuidance());
                return;
            }
            catch (Exception ex)
            {
                Status("Could not read default location:\r\n" + ex.Message);
                return;
            }

            sb.Append("\r\nEffective position (what apps get now):\r\n");
            try
            {
                var geo = new Geolocator();
                sb.Append("  status: " + geo.LocationStatus + "\r\n");
                var task = geo.GetGeopositionAsync().AsTask();
                var done = await Task.WhenAny(task, Task.Delay(20000));
                if (done != task)
                {
                    sb.Append("  (timed out after 20s — no live source and no usable default)");
                }
                else
                {
                    var pos = task.Result.Coordinate;
                    sb.Append("  " + pos.Point.Position.Latitude + ", " + pos.Point.Position.Longitude +
                        "  (~" + (int)pos.Accuracy + " m, source: " + pos.PositionSource + ")");
                }
            }
            catch (UnauthorizedAccessException)
            {
                sb.Append("  denied — " + AccessGuidance());
            }
            catch (Exception ex)
            {
                sb.Append("  unavailable: " + ex.Message);
            }
            Status(sb.ToString());
        }

        private async void BtnIp_Click(object sender, EventArgs e)
        {
            Status("Detecting country from IP address...");
            IpCountry.IpGeo geo;
            try
            {
                geo = await Task.Run(() => IpCountry.Fetch());
            }
            catch (Exception ex)
            {
                Status("IP lookup failed (no internet?):\r\n" + ex.Message);
                return;
            }
            var point = geo.ResolveLatLng();
            if (point == null)
            {
                Status("IP country found (" + geo.Label() + ") but no coordinates available.");
                return;
            }
            var r = MessageBox.Show(
                "IP: " + geo.Ip + "\nCountry: " + geo.Country + "\nCity: " + geo.City +
                "\n\nMock Windows default location to:\n" + point.Value.lat + ", " + point.Value.lng + "?",
                "IP country detected", MessageBoxButtons.YesNo);
            if (r == DialogResult.Yes)
            {
                txtLat.Text = point.Value.lat.ToString();
                txtLng.Text = point.Value.lng.ToString();
                BtnSet_Click(sender, e);
            }
            else
            {
                await RefreshStatus();
            }
        }

        private void BtnSettings_Click(object sender, EventArgs e)
        {
            try
            {
                Process.Start(new ProcessStartInfo("ms-settings:privacy-location") { UseShellExecute = true });
            }
            catch (Exception ex)
            {
                Status("Could not open Settings:\r\n" + ex.Message);
            }
        }

        private bool TryParse(out double lat, out double lng)
        {
            lat = 0; lng = 0;
            if (!double.TryParse(txtLat.Text.Trim(), System.Globalization.NumberStyles.Float,
                    System.Globalization.CultureInfo.InvariantCulture, out lat) ||
                !double.TryParse(txtLng.Text.Trim(), System.Globalization.NumberStyles.Float,
                    System.Globalization.CultureInfo.InvariantCulture, out lng) ||
                lat < -90 || lat > 90 || lng < -180 || lng > 180)
            {
                MessageBox.Show("Enter valid latitude (-90…90) and longitude (-180…180). Use dot as decimal separator.");
                return false;
            }
            return true;
        }
    }
}

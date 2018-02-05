/*
  Copyright © 2017 Kerry Shetline, kerry@shetline.com

  MIT license: https://opensource.org/licenses/MIT

  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
  persons to whom the Software is furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
  Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
  WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
  OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export const SVC_MIN_YEAR = -6000;
export const SVC_MAX_YEAR = 9999;

interface IpLocation {
  city: string;
  countryCode: string;
  lat: number;
  lon: number;
  region: string;
  status: string;
  timezone: string;
}

@Injectable()
export class AppService {
  private knownIanaTimezones: Set<String>;

  constructor(private httpClient: HttpClient) {
  }

  public setKnownIanaTimezones(zones: Set<String>): void {
    this.knownIanaTimezones = zones;
  }

  public isKnownIanaTimezone(zone: string): boolean {
    return this.knownIanaTimezones && this.knownIanaTimezones.has(zone);
  }

  public getTimeZoneFromIp(): Promise<string> {
    return this.httpClient.jsonp('http://ip-api.com/json', 'callback').toPromise().then((location: IpLocation) => {
      if (location.status === 'success' && location.timezone && this.knownIanaTimezones.has(location.timezone)) {
        return location.timezone;
      }
      else
        return null;
    });
  }
}

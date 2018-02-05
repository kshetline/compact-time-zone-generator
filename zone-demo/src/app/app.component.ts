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

import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { KsTimeZone } from './util/ks-timezone';
import { YMDDate } from './util/ks-calendar';
import { KsDateTime } from './util/ks-date-time';
import * as _ from 'lodash';
import { Observable } from 'rxjs/Rx';
import { Subscription } from 'rxjs/Subscription';
import { AppService, SVC_MAX_YEAR, SVC_MIN_YEAR } from './app.service';
import * as M_ from './util/ks-math';
import { DateAndTime } from './util/ks-date-time-zone-common';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnDestroy, OnInit {
  private dateTime = new KsDateTime(null, KsTimeZone.OS_ZONE);
  private _time: number = this.dateTime.utcTimeMillis;
  private _calendarYMD: YMDDate = {y: this.dateTime.wallTime.y, m: this.dateTime.wallTime.m, d: this.dateTime.wallTime.d};
  private _zoneName = ['', ''];
  private _timeZone: KsTimeZone[] = [KsTimeZone.OS_ZONE, KsTimeZone.UT_ZONE];
  private _longitude = 0;
  private lmtLongitude = 0;
  private _trackTime = false;
  private timer: Subscription;

  disabled = [false, false];
  error = ['', ''];
  showLongitude = false;

  minYear: number = SVC_MIN_YEAR;
  maxYear: number = SVC_MAX_YEAR;

  constructor(private appService: AppService) {
  }

  ngOnInit(): void {
    this.zoneName0 = 'OS';
    this.zoneName1 = 'UT';
    this.updateCalendar();

    this.appService.getTimeZoneFromIp().then(timeZone => {
      if (timeZone)
        this.zoneName0 = timeZone;
    });
  }

  ngOnDestroy(): void {
    this.stopTimer();
  }

  stopTimer(): void {
    if (this.timer) {
      this.timer.unsubscribe();
      this.timer = undefined;
    }
  }

  get timeZone0(): KsTimeZone { return this._timeZone[0]; }
  get timeZone1(): KsTimeZone { return this._timeZone[1]; }

  get zoneName0(): string { return this._zoneName[0]; }
  get zoneName1(): string { return this._zoneName[1]; }
  set zoneName0(newZone: string) {
    this.setZoneName(newZone, 0);
  }
  set zoneName1(newZone: string) {
    this.setZoneName(newZone, 1);
  }

  private setZoneName(newZone: string, i: number): void {
    if (newZone && this._zoneName[i] !== newZone) {
      this._zoneName[i] = newZone;
      this._timeZone[i] = KsTimeZone.getTimeZone(newZone, this.lmtLongitude);
      this.showLongitude = (this._zoneName[0] === 'LMT' || this._zoneName[1] === 'LMT');

      if (i === 0)
        this.dateTime.timeZone = this._timeZone[0];

      this.updateCalendar();
    }
  }

  get time(): number { return this._time; }
  set time(newTime: number) {
    if (this._time !== newTime) {
      this._time = newTime;
      this.dateTime.utcTimeMillis = newTime;

      if (this._calendarYMD.y !== this.dateTime.wallTime.y || this._calendarYMD.m !== this.dateTime.wallTime.m ||
          this._calendarYMD.d !== this.dateTime.wallTime.d) {
        this.updateCalendar();
      }
    }
  }

  get calendarYMD(): YMDDate { return this._calendarYMD; }
  @Input() set calendarYMD(newYMD: YMDDate) {
    if (!_.isEqual(this._calendarYMD, newYMD)) {
      this._calendarYMD = newYMD;
      this.dateTime.wallTime = {y: newYMD.y, m: newYMD.m, d: newYMD.d,
        hrs: (_.isUndefined((<DateAndTime> newYMD).hrs) ? 12 : (<DateAndTime> newYMD).hrs),
        min: (_.isUndefined((<DateAndTime> newYMD).min) ?  0 : (<DateAndTime> newYMD).min),
        sec: (_.isUndefined((<DateAndTime> newYMD).sec) ?  0 : (<DateAndTime> newYMD).sec)};
      this.time = this.dateTime.utcTimeMillis;
    }
  }

  get trackTime(): boolean { return this._trackTime; }
  set trackTime(value: boolean) {
    if (this._trackTime !== value) {
      this._trackTime = value;

      if (value) {
        this.timer = Observable.timer(250, 250).subscribe(() => {
          this.time = new Date().getTime();
        });
      }
      else
        this.stopTimer();
    }
  }

  get longitude(): number { return this._longitude; }
  set longitude(newLongitude: number) {
    if (this._longitude !== newLongitude) {
      this._longitude = (newLongitude ? newLongitude : 0);

      const lmtLongitude = M_.mod2(Math.round(newLongitude * 4) / 4, 360);

      if (this.lmtLongitude !== lmtLongitude) {
        this.lmtLongitude = lmtLongitude;

        for (let i = 0; i <= 1; ++i) {
          if (this._zoneName[i] === 'LMT') {
            this._timeZone[i] = KsTimeZone.getTimeZone('LMT', lmtLongitude);
          }
        }
      }
    }
  }

  updateCalendar(): void {
    this.calendarYMD = <YMDDate> {y: this.dateTime.wallTime.y, m: this.dateTime.wallTime.m, d: this.dateTime.wallTime.d,
      hrs: this.dateTime.wallTime.hrs, min: this.dateTime.wallTime.min, sec: this.dateTime.wallTime.sec};
  }

  setToNow(): void {
    this.time = Date.now();
  }
}

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

import * as _ from 'lodash';
import { Component, EventEmitter, forwardRef, OnInit, Output } from '@angular/core';
import { NG_VALUE_ACCESSOR, ControlValueAccessor } from '@angular/forms';
import { Observable } from 'rxjs';
import { AppService } from '../../app.service';
import { KsTimeZone, RegionAndSubzones } from '../../util/ks-timezone';

export const SVC_ZONE_SELECTOR_VALUE_ACCESSOR: any = {
  provide: NG_VALUE_ACCESSOR,
  useExisting: forwardRef(() => SvcZoneSelectorComponent),
  multi: true
};

const noop = () => {};

const MISC_OPTION = '- Miscellaneous -';
const UT_OPTION   = '- UTC hour offsets -';
const OS_OPTION   = '- Your OS time zone -';
const LMT_OPTION  = '- Local Mean Time -';

const MISC = 'MISC';
const UT   = 'UT';
const OS   = 'OS';
const LMT  = 'LMT';

@Component({
  selector: 'svc-zone-selector',
  templateUrl: './svc-zone-selector.component.html',
  styleUrls: ['./svc-zone-selector.component.scss'],
  providers: [SVC_ZONE_SELECTOR_VALUE_ACCESSOR]
})
export class SvcZoneSelectorComponent implements ControlValueAccessor, OnInit {
  public disabled = false;
  public regions: string[] = [UT_OPTION];
  public subzones: string[] = [UT];

  private _region: string = this.regions[0];
  private _subzone: string = this.subzones[0];
  private _value: string = UT;
  private lastSubzones: {[region: string]: string} = {};
  private subzonesByRegion: {[region: string]: string[]} = {};
  private hasFocus = false;
  private focusCount = 0;
  private onTouchedCallback: () => void = noop;
  private onChangeCallback: (_: any) => void = noop;
  private knownIanaZones = new Set<String>();

  error: string;

  @Output() onFocus: EventEmitter<any> = new EventEmitter();
  @Output() onBlur: EventEmitter<any> = new EventEmitter();

  get value(): string | null {
    if (!this._region || _.isNil(this._subzone)) {
      return null;
    }
    else if (this._region === MISC_OPTION || this._region === UT_OPTION) {
      return this._subzone;
    }
    else if (this._region === LMT_OPTION) {
      return LMT;
    }
    else if (this._region === OS_OPTION) {
      return OS;
    }

    return (this._region + '/' + this._subzone).replace(/ /g, '_');
  }
  set value(newZone: string) {
    if (this._value !== newZone) {
      this._value = newZone;
      this.updateValue(newZone);
      this.onChangeCallback(newZone);
    }
  }

  updateValue(newZone: string): void {
    if (newZone === null) {
      this._region = this._subzone = this._value = null;

      return;
    }

    const groups: string[] = /(America\/Argentina\/|America\/Indiana\/|SystemV\/\w+|\w+\/|[-+:0-9A-Za-z]+)(.+)?/.exec(newZone);

    if (groups) {
      let g1 = groups[1];
      const g2 = groups[2];

      if (g1.endsWith('/')) {
        g1 = groups[1].slice(0, -1);
      }

      if (_.isUndefined(g2)) {
        if (g1.startsWith(UT)) {
          this.setRegion(UT_OPTION);
          this.subzone = g1;
        }
        else if (g1 === LMT) {
          this.setRegion(LMT_OPTION);
          this.subzone = '';
        }
        else if (g1 === OS) {
          this.setRegion(OS_OPTION);
          this.subzone = '';
        }
        else {
          this.setRegion(MISC_OPTION);
          this.subzone = g1;
        }
      }
      else {
        this.setRegion(g1);
        this.subzone = g2.replace(/_/g, ' ');
      }
    }
    else {
      this.setRegion(UT_OPTION);
      this.subzone = UT;
    }
  }

  onDropdownFocus(event: any): void {
    this.hasFocus = true;

    if (this.focusCount++ === 0) {
      this.onFocus.emit(event);
    }
  }

  onDropdownBlur(event: any): void {
    this.hasFocus = false;
    // If focus is lost and hasn't come back to a different selection on the next event cycle, assume
    // the selector as a whole has lost focus.
    Observable.timer().subscribe(() => {
        --this.focusCount;

      if (!this.hasFocus) {
        this.onTouchedCallback();
        this.onBlur.emit(event);
      }
    });
  }

  writeValue(newZone: any): void {
    if (this._value !== newZone) {
      this.updateValue(newZone);
    }
  }

  registerOnChange(fn: any): void {
    this.onChangeCallback = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouchedCallback = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  get region(): string { return this._region; }
  set region(newRegion: string) { this.setRegion(newRegion, true); }

  get subzone(): string { return this._subzone; }
  set subzone(newZone: string) {
    if (!newZone)
      return;

    if (this._subzone !== newZone) {
      this._subzone = newZone;
      this.lastSubzones[this._region] = newZone;
      this._value = this.value;
      this.onChangeCallback(this._value);
    }
  }

  constructor(private appService: AppService) {
    this.lastSubzones[this._region] = this._subzone;
    this.subzonesByRegion[this._region] = this.subzones;
  }

  ngOnInit(): void {
    this.supplementAndProcessZones(KsTimeZone.getRegionsAndSubzones());
  }

  private supplementAndProcessZones(data: RegionAndSubzones[]): void {
    data.forEach((region: RegionAndSubzones) => {
      region.subzones.forEach((subzone: string) => {
        const zone = (region.region === MISC ? '' : region.region + '/') + subzone.replace(/ /g, '_');
        this.knownIanaZones.add(zone);
      });
    });

    this.appService.setKnownIanaTimezones(this.knownIanaZones);

    const hourOffsets: string[] = [];

    for (let h = -12; h <= 14; ++h) {
      const habs = Math.abs(h);

      hourOffsets.push('UT' + (h === 0 ? '' : (h > 0 ? '+' : '-') + (habs < 10 ? '0' : '') + habs + ':00'));
    }

    data.push({region: UT_OPTION,  subzones: hourOffsets});
    data.push({region: OS_OPTION,  subzones: []});
    data.push({region: LMT_OPTION, subzones: []});

    data.forEach((region: RegionAndSubzones) => {
      if (region.region === MISC) {
        region.region = MISC_OPTION;
      }

      this.subzonesByRegion[region.region] = region.subzones;

      if (region.region === this._region) {
        this.subzones = region.subzones;
      }
    });

    this.regions = data.map((region: RegionAndSubzones) => region.region);
  }

  private setRegion(newRegion: string, doChangeCallback?: boolean): void {
    if (this._region !== newRegion) {
      this._region = newRegion;
      this._subzone = '';

      const subzones = this.subzonesByRegion[newRegion];

      if (subzones) {
        this.subzones = subzones;
      }
      else {
        this.subzones = [];
      }

      const lastSubzone = this.lastSubzones[newRegion];

      if (lastSubzone) {
        this._subzone = lastSubzone;
      }
      else if (this.subzones.length > 0) {
        this._subzone = this.subzones[0];
        this.lastSubzones[this._region] = this._subzone;
      }

      if (this.subzones.length > 0 && this.subzone)
        this._value = this.value;
      else if (newRegion === LMT_OPTION)
        this._value = LMT;
      else if (this._region === OS_OPTION)
        this._value = OS;

      if (doChangeCallback)
        this.onChangeCallback(this._value);
    }
  }
}

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

import { BrowserModule } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FlexLayoutModule } from '@angular/flex-layout';
import { HttpClientModule, HttpClientJsonpModule } from '@angular/common/http';

import { BlockUIModule, ButtonModule, CheckboxModule, DropdownModule, SharedModule } from 'primeng/primeng';

import { AppComponent } from './app.component';
import { AppService } from './app.service';

import { KsCalendarComponent } from './widgets/ks-calendar/ks-calendar.component';
import { KsCheckboxComponent } from './widgets/ks-checkbox/ks-checkbox.component';
import { KsDropdownComponent } from './widgets/ks-dropdown/ks-dropdown.component';
import { KsSequenceEditorComponent } from './widgets/ks-sequence-editor/ks-sequence-editor.component';
import { SvcAngleEditorComponent } from './svc/svc-angle-editor.component';
import { SvcDateEditorComponent } from './svc/svc-date-editor.component';
import { SvcTimeEditorComponent } from './svc/svc-time-editor.component';
import { SvcZoneSelectorComponent } from './svc/svc-zone-selector/svc-zone-selector.component';

@NgModule({
  declarations: [
    AppComponent,
    KsCalendarComponent,
    KsCheckboxComponent,
    KsDropdownComponent,
    KsSequenceEditorComponent,
    SvcAngleEditorComponent,
    SvcDateEditorComponent,
    SvcTimeEditorComponent,
    SvcZoneSelectorComponent
  ],
  imports: [
    BlockUIModule,
    BrowserAnimationsModule,
    BrowserModule,
    ButtonModule,
    CheckboxModule,
    DropdownModule,
    FlexLayoutModule,
    FormsModule,
    HttpClientModule,
    HttpClientJsonpModule,
    SharedModule
  ],
  providers: [
    AppService,
    DatePipe
  ],
  bootstrap: [AppComponent],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class AppModule { }

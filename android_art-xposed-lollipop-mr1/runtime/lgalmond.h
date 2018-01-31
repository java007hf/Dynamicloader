/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ART_RUNTIME_LGALMOND_H_
#define ART_RUNTIME_LGALMOND_H_

#include <string>

#include "globals.h"
#include "os.h"

namespace art {

class LGAlmond {
 public:
  static const uint8_t kOatMagic[4];
  static const uint8_t kOarmBrand[20];

  // Initializes the LG Almond encryption library, if available.
  static void Init();

  static bool IsEncryptedDex(const void* data, size_t size);
  static bool DecryptDex(void* data, size_t* size);
  static bool IsEncryptedOat(const void* data);
  static bool DecryptOat(void* data, const File& file, std::string* error_msg);

 private:
  static const uint32_t kFormatDex = 1;

  typedef int (*IsDRMDexFn)(const void*, size_t);
  typedef int (*CopyDexToMemFn)(void*, size_t, size_t*, uint8_t*, uint8_t*);
  typedef int (*DecOatFn)(void*, size_t, uint8_t*, uint8_t*);

  static IsDRMDexFn IsDRMDex_;
  static CopyDexToMemFn CopyDexToMem_;
  static DecOatFn DecOat_;
};

}  // namespace art

#endif  // ART_RUNTIME_LGALMOND_H_

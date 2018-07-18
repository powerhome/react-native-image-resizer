import { NativeModules } from 'react-native';

const ImageResizerAndroid = NativeModules.ImageResizerAndroid;

export default {
  createResizedImage: (imagePath, newWidth, newHeight, compressFormat, quality, rotation = 0) => {
    return new Promise((resolve, reject) => {
      ImageResizerAndroid.createResizedImage(
        imagePath,
        newWidth,
        newHeight,
        compressFormat,
        quality,
        rotation,
        resolve,
        reject
      );
    });
  }
};

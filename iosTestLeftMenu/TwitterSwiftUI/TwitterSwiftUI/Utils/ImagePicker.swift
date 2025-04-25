//
//  ImagePicker.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/24.
//

import SwiftUI

struct ImagePicker: UIViewControllerRepresentable {
    
    @Binding var selectedImage: UIImage?
    @Environment(\.presentationMode) var presentationMode
    
    func makeCoordinator() -> Coordinator {
        return .init(parent: self)
    }
    
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        return picker
    }
    
    func updateUIViewController(_ uiView: UIImagePickerController, context: Context) {
        
    }
}

extension ImagePicker {
    class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let  parent: ImagePicker
        
        init(parent: ImagePicker) {
            self.parent = parent
        }

        // Deleagte
        func imagePickerController(_ picker: UIImagePickerController, 
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            guard let image = info[.originalImage] as? UIImage else { return }
            // 選択されたイメージを親に渡して画面を閉じる
            parent.selectedImage = image
            parent.presentationMode.wrappedValue.dismiss()
        }
    }
}

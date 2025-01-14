import SwiftUI
import UIKit

public class OriginalImageMarkView: UIView {
    private let stackView: UIStackView = {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.spacing = 4
        stack.alignment = .center
        return stack
    }()
    
    private let iconLabel: UILabel = {
        let label = UILabel()
        label.text = "HD"
        label.textColor = .white
        label.font = .systemFont(ofSize: 12, weight: .bold)
        return label
    }()
    
    private let sizeLabel: UILabel = {
        let label = UILabel()
        label.textColor = .white
        label.font = .systemFont(ofSize: 11)
        return label
    }()
    
    public init(imageSize: Int?) {
        super.init(frame: .zero)
        setupView(imageSize: imageSize)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupView(imageSize: Int?) {
        backgroundColor = UIColor.black.withAlphaComponent(0.6)
        layer.cornerRadius = 4
        clipsToBounds = true
        
        addSubview(stackView)
        stackView.addArrangedSubview(iconLabel)
        if let size = imageSize {
            let mbSize = Double(size) / 1024.0 / 1024.0
            sizeLabel.text = String(format: "%.1fMB", mbSize)
            stackView.addArrangedSubview(sizeLabel)
        }
        
        stackView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stackView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 6),
            stackView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -6),
            stackView.topAnchor.constraint(equalTo: topAnchor, constant: 4),
            stackView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4)
        ])
    }
} 